"""Long-running analysis server for the PythonX editor.

Reads one JSON request per line on stdin: {"path": ..., "source": ...}
Writes one JSON reply per line on stdout: {"problems": [...], "symbols": [...]}

Problems come from the real compiler (syntax errors and SyntaxWarnings) plus two
cheap, low-noise checks over the AST: names that are never defined anywhere in the
file (typos like `pritn`) and imports that are never used.
"""
import ast
import builtins
import json
import sys
import warnings

BUILTINS = set(dir(builtins)) | {"__file__", "__name__", "__doc__", "__builtins__", "__spec__",
                                 "__loader__", "__package__", "__path__", "__annotations__", "__dict__"}

# Pattern-matching and type-parameter nodes that bind a name (absent on older Pythons).
NAMED_PATTERNS = tuple(getattr(ast, n) for n in ("MatchAs", "MatchStar", "TypeVar", "ParamSpec", "TypeVarTuple")
                       if hasattr(ast, n))
MAPPING_PATTERNS = tuple(getattr(ast, n) for n in ("MatchMapping",) if hasattr(ast, n))


def problem(line, col, end_line, end_col, msg, error):
    return {"line": line or 1, "col": max(col or 1, 1), "endLine": end_line or line or 1,
            "endCol": max(end_col or ((col or 1) + 1), 1), "msg": msg, "error": error}


def defined_names(tree):
    """Every name bound anywhere in the module, ignoring scopes (so the check never reports false errors)."""
    names = set()
    star_import = False
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef, ast.ClassDef)):
            names.add(node.name)
        elif isinstance(node, ast.arg):
            names.add(node.arg)
        elif isinstance(node, ast.Name) and isinstance(node.ctx, (ast.Store, ast.Del)):
            names.add(node.id)
        elif isinstance(node, (ast.Import, ast.ImportFrom)):
            for alias in node.names:
                if alias.name == "*":
                    star_import = True
                else:
                    names.add((alias.asname or alias.name).split(".")[0])
        elif isinstance(node, (ast.Global, ast.Nonlocal)):
            names.update(node.names)
        elif isinstance(node, ast.ExceptHandler) and node.name:
            names.add(node.name)
        elif isinstance(node, NAMED_PATTERNS) and getattr(node, "name", None):
            names.add(node.name)
        elif isinstance(node, MAPPING_PATTERNS) and getattr(node, "rest", None):
            names.add(node.rest)
    return names, star_import


def dynamic_scope(tree):
    """True if the module can create names the AST can't see."""
    for node in ast.walk(tree):
        if isinstance(node, ast.Call) and isinstance(node.func, ast.Name) and \
                node.func.id in {"exec", "eval", "globals", "locals", "vars", "__import__"}:
            return True
    return False


def undefined_names(tree):
    names, star = defined_names(tree)
    if star or dynamic_scope(tree):
        return []
    found = []
    seen = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Name) and isinstance(node.ctx, ast.Load):
            if node.id not in names and node.id not in BUILTINS and (node.id, node.lineno) not in seen:
                seen.add((node.id, node.lineno))
                found.append(problem(node.lineno, node.col_offset + 1, node.end_lineno, node.end_col_offset + 1,
                                     f"'{node.id}' is not defined", True))
    return found


def unused_imports(tree, source):
    if "__all__" in source:
        return []
    imported = {}
    for node in tree.body:
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            if isinstance(node, ast.ImportFrom) and node.module == "__future__":
                continue
            for alias in node.names:
                if alias.name == "*":
                    continue
                local = (alias.asname or alias.name).split(".")[0]
                imported[local] = node
    if not imported:
        return []
    used = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Name):
            used.add(node.id)
        elif isinstance(node, ast.Attribute):
            base = node
            while isinstance(base, ast.Attribute):
                base = base.value
            if isinstance(base, ast.Name):
                used.add(base.id)
    # Names referenced only from string annotations still count.
    for node in ast.walk(tree):
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            for name in imported:
                if name in node.value:
                    used.add(name)
    return [problem(node.lineno, node.col_offset + 1, node.lineno, node.end_col_offset + 1,
                    f"'{name}' is imported but never used", False)
            for name, node in imported.items() if name not in used]


def symbols(tree):
    out = []

    def visit(body, depth):
        for node in body:
            if isinstance(node, ast.ClassDef):
                out.append({"name": node.name, "kind": "class", "line": node.lineno, "depth": depth})
                visit(node.body, depth + 1)
            elif isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                args = ", ".join(a.arg for a in node.args.args)
                out.append({"name": f"{node.name}({args})", "kind": "method" if depth else "function",
                            "line": node.lineno, "depth": depth})
                visit(node.body, depth + 1)

    visit(tree.body, 0)
    return out


def analyze(source, path):
    problems = []
    tree = None
    with warnings.catch_warnings(record=True) as caught:
        warnings.simplefilter("always")
        try:
            tree = ast.parse(source, path)
            compile(tree, path, "exec", dont_inherit=True)
        except SyntaxError as e:
            col = e.offset or 1
            problems.append(problem(e.lineno, col, getattr(e, "end_lineno", None),
                                    getattr(e, "end_offset", None), e.msg, True))
            tree = None
        except (ValueError, RecursionError) as e:
            problems.append(problem(1, 1, 1, 2, str(e), True))
            tree = None
    for w in caught:
        if issubclass(w.category, SyntaxWarning):
            problems.append(problem(w.lineno, 1, w.lineno, 200, str(w.message), False))
    syms = []
    if tree is not None:
        problems += undefined_names(tree)
        problems += unused_imports(tree, source)
        syms = symbols(tree)
    problems.sort(key=lambda p: (p["line"], p["col"]))
    return {"problems": problems[:200], "symbols": syms}


def main():
    sys.setrecursionlimit(3000)
    for line in sys.stdin:
        try:
            request = json.loads(line)
            reply = analyze(request.get("source", ""), request.get("path", "<editor>"))
        except Exception as e:  # never die: the editor keeps one server per session
            reply = {"problems": [], "symbols": [], "crash": repr(e)}
        sys.stdout.write(json.dumps(reply) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
