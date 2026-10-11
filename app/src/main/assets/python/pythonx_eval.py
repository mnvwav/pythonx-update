"""Quick Run server for the PythonX home screen.

One JSON request per line on stdin: {"code": "..."}; one JSON reply per line on stdout:
{"out": printed text, "value": repr of an expression's result or null, "error": short error or null}.
Names defined by earlier requests stay available, like a REPL.
"""
import ast
import builtins
import contextlib
import io
import json
import sys
import traceback

_protocol_out = sys.stdout
namespace = {"__name__": "__quickrun__", "__builtins__": builtins}


def _no_input(prompt=""):
    raise RuntimeError("input() isn't available in Quick Run. Put the code in a project and press Run.")


namespace["input"] = _no_input


def run(code):
    out = io.StringIO()
    value = None
    error = None
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
        try:
            tree = ast.parse(code, "<quick run>", "exec")
            # Like the REPL: if the last statement is an expression, show its value.
            last = None
            if tree.body and isinstance(tree.body[-1], ast.Expr):
                last = ast.Expression(tree.body.pop().value)
            exec(compile(tree, "<quick run>", "exec"), namespace)
            if last is not None:
                result = eval(compile(last, "<quick run>", "eval"), namespace)
                if result is not None:
                    namespace["_"] = result
                    value = repr(result)
        except SyntaxError as e:
            error = f"SyntaxError: {e.msg}" + (f" (line {e.lineno})" if e.lineno and e.lineno > 1 else "")
        except BaseException as e:  # SystemExit and KeyboardInterrupt included: keep serving
            frames = traceback.extract_tb(e.__traceback__)
            line = next((f.lineno for f in reversed(frames) if f.filename == "<quick run>"), None)
            error = f"{type(e).__name__}: {e}" + (f" (line {line})" if line and "\n" in code else "")
    text = out.getvalue()
    if len(text) > 20000:
        text = text[:20000] + "\n… output truncated"
    if value is not None and len(value) > 5000:
        value = value[:5000] + " …"
    return {"out": text, "value": value, "error": error}


def main():
    for line in sys.stdin:
        try:
            reply = run(json.loads(line).get("code", ""))
        except Exception as e:
            reply = {"out": "", "value": None, "error": repr(e)}
        _protocol_out.write(json.dumps(reply) + "\n")
        _protocol_out.flush()


if __name__ == "__main__":
    main()
