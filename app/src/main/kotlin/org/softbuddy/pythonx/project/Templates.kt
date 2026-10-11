package org.softbuddy.pythonx.project

private val HELLO = """
def main():
    name = input("What's your name? ").strip() or "World"
    print(f"Hello, {name}!")


if __name__ == "__main__":
    main()
""".trimStart()

private val CALCULATOR = """
""${'"'}A calculator that evaluates arithmetic safely, without eval().""${'"'}
import ast
import operator

OPERATORS = {
    ast.Add: operator.add,
    ast.Sub: operator.sub,
    ast.Mult: operator.mul,
    ast.Div: operator.truediv,
    ast.FloorDiv: operator.floordiv,
    ast.Mod: operator.mod,
    ast.Pow: operator.pow,
    ast.USub: operator.neg,
    ast.UAdd: operator.pos,
}


def evaluate(node):
    if isinstance(node, ast.Expression):
        return evaluate(node.body)
    if isinstance(node, ast.Constant) and isinstance(node.value, (int, float)):
        return node.value
    if isinstance(node, ast.BinOp) and type(node.op) in OPERATORS:
        return OPERATORS[type(node.op)](evaluate(node.left), evaluate(node.right))
    if isinstance(node, ast.UnaryOp) and type(node.op) in OPERATORS:
        return OPERATORS[type(node.op)](evaluate(node.operand))
    raise ValueError("unsupported expression")


def main():
    print("Enter an expression like 2 * (3 + 4). Type q to quit.")
    while True:
        text = input("> ").strip()
        if text.lower() in {"q", "quit", "exit"}:
            break
        if not text:
            continue
        try:
            print(evaluate(ast.parse(text, mode="eval")))
        except ZeroDivisionError:
            print("Error: division by zero")
        except (SyntaxError, ValueError) as error:
            print(f"Error: {error}")


if __name__ == "__main__":
    main()
""".trimStart()

private val GUESS = """
import random


def play(low=1, high=100):
    secret = random.randint(low, high)
    attempts = 0
    print(f"I'm thinking of a number between {low} and {high}.")
    while True:
        guess = input("Your guess: ").strip()
        if not guess.isdigit():
            print("Please enter a whole number.")
            continue
        attempts += 1
        number = int(guess)
        if number < secret:
            print("Too low!")
        elif number > secret:
            print("Too high!")
        else:
            print(f"Correct! You got it in {attempts} attempts.")
            return attempts


def main():
    best = None
    while True:
        attempts = play()
        best = attempts if best is None else min(best, attempts)
        print(f"Best score: {best}")
        if input("Play again? (y/n) ").strip().lower() != "y":
            break


if __name__ == "__main__":
    main()
""".trimStart()

private val CLI = """
""${'"'}A command-line tool with subcommands, built on argparse.

Try it from the terminal:
    python3 main.py count --words notes.txt
    python3 main.py stats 3 1 4 1 5 9
Set default arguments for Run in the project's run configuration.
""${'"'}
import argparse
import statistics
import sys
from pathlib import Path


def count(args):
    text = Path(args.file).read_text()
    if args.words:
        print(len(text.split()), "words")
    else:
        print(len(text.splitlines()), "lines")


def stats(args):
    numbers = args.numbers
    print(f"count  {len(numbers)}")
    print(f"mean   {statistics.mean(numbers):.3f}")
    print(f"median {statistics.median(numbers):.3f}")
    if len(numbers) > 1:
        print(f"stdev  {statistics.stdev(numbers):.3f}")


def main(argv=None):
    parser = argparse.ArgumentParser(prog="tool", description=__doc__.splitlines()[0])
    commands = parser.add_subparsers(dest="command", required=True)

    p = commands.add_parser("count", help="count lines or words in a file")
    p.add_argument("file")
    p.add_argument("--words", action="store_true", help="count words instead of lines")
    p.set_defaults(handler=count)

    p = commands.add_parser("stats", help="summary statistics for numbers")
    p.add_argument("numbers", nargs="+", type=float)
    p.set_defaults(handler=stats)

    args = parser.parse_args(argv)
    args.handler(args)


if __name__ == "__main__":
    main(sys.argv[1:] or ["stats", "3", "1", "4", "1", "5", "9"])
""".trimStart()

private val NOTES_TXT = "Python on your phone.\nEdit, run and share scripts anywhere.\n"

private val WEATHER = """
""${'"'}Current weather from the free Open-Meteo API (no key needed).""${'"'}
import json
import urllib.parse
import urllib.request

GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
FORECAST_URL = "https://api.open-meteo.com/v1/forecast"


def get_json(url, **params):
    with urllib.request.urlopen(f"{url}?{urllib.parse.urlencode(params)}", timeout=15) as response:
        return json.load(response)


def main():
    city = input("City: ").strip() or "London"
    places = get_json(GEOCODING_URL, name=city, count=1).get("results")
    if not places:
        print(f"Could not find {city!r}.")
        return
    place = places[0]
    weather = get_json(
        FORECAST_URL,
        latitude=place["latitude"],
        longitude=place["longitude"],
        current="temperature_2m,wind_speed_10m,relative_humidity_2m",
    )["current"]
    print(f"{place['name']}, {place.get('country', '')}")
    print(f"Temperature  {weather['temperature_2m']} °C")
    print(f"Humidity     {weather['relative_humidity_2m']} %")
    print(f"Wind         {weather['wind_speed_10m']} km/h")


if __name__ == "__main__":
    main()
""".trimStart()

private val SCRAPER = """
""${'"'}Lists the links on a web page using only the standard library.""${'"'}
from html.parser import HTMLParser
from urllib.parse import urljoin
from urllib.request import Request, urlopen


class LinkParser(HTMLParser):
    def __init__(self, base):
        super().__init__()
        self.base = base
        self.links = []
        self._text = None

    def handle_starttag(self, tag, attrs):
        if tag == "a":
            href = dict(attrs).get("href")
            if href and not href.startswith(("#", "javascript:")):
                self._text = []
                self.links.append([urljoin(self.base, href), ""])

    def handle_data(self, data):
        if self._text is not None:
            self._text.append(data.strip())

    def handle_endtag(self, tag):
        if tag == "a" and self._text is not None:
            self.links[-1][1] = " ".join(t for t in self._text if t)
            self._text = None


def main():
    url = input("URL [https://www.python.org]: ").strip() or "https://www.python.org"
    request = Request(url, headers={"User-Agent": "PythonX"})
    with urlopen(request, timeout=20) as response:
        html = response.read().decode(response.headers.get_content_charset() or "utf-8", "replace")
    parser = LinkParser(url)
    parser.feed(html)
    seen = set()
    for href, text in parser.links:
        if href in seen:
            continue
        seen.add(href)
        print(f"{text[:40]:<40}  {href}")
    print(f"\n{len(seen)} unique links")


if __name__ == "__main__":
    main()
""".trimStart()

private val NOTES = """
""${'"'}A command-line notes app that stores notes in notes.db next to this script.""${'"'}
import sqlite3
from pathlib import Path

DATABASE = Path(__file__).with_name("notes.db")


def connect():
    connection = sqlite3.connect(DATABASE)
    connection.execute(
        "CREATE TABLE IF NOT EXISTS notes ("
        "id INTEGER PRIMARY KEY, text TEXT NOT NULL, "
        "created TEXT DEFAULT CURRENT_TIMESTAMP)"
    )
    return connection


def main():
    with connect() as db:
        while True:
            command = input("\n[a]dd, [l]ist, [s]earch, [d]elete, [q]uit: ").strip().lower()
            if command == "a":
                text = input("Note: ").strip()
                if text:
                    db.execute("INSERT INTO notes (text) VALUES (?)", (text,))
                    print("Saved.")
            elif command in ("l", "s"):
                term = input("Search for: ").strip() if command == "s" else ""
                rows = db.execute(
                    "SELECT id, created, text FROM notes WHERE text LIKE ? ORDER BY id", (f"%{term}%",)
                ).fetchall()
                if not rows:
                    print("No notes.")
                for note_id, created, text in rows:
                    print(f"{note_id:>3}  {created}  {text}")
            elif command == "d":
                note_id = input("Note id: ").strip()
                deleted = db.execute("DELETE FROM notes WHERE id = ?", (note_id,)).rowcount
                print("Deleted." if deleted else "No note with that id.")
            elif command == "q":
                break


if __name__ == "__main__":
    main()
""".trimStart()

private val CSV_MAIN = """
""${'"'}Reads sales.csv and prints summary statistics using only the standard library.""${'"'}
import csv
import statistics
from collections import defaultdict
from pathlib import Path

DATA = Path(__file__).with_name("sales.csv")


def main():
    with DATA.open(newline="") as file:
        rows = list(csv.DictReader(file))

    amounts = [float(row["amount"]) for row in rows]
    print(f"Rows     {len(rows)}")
    print(f"Total    {sum(amounts):.2f}")
    print(f"Average  {statistics.mean(amounts):.2f}")
    print(f"Median   {statistics.median(amounts):.2f}")

    by_region = defaultdict(float)
    for row in rows:
        by_region[row["region"]] += float(row["amount"])
    print("\nSales by region")
    width = max(len(region) for region in by_region)
    top = max(by_region.values())
    for region, total in sorted(by_region.items(), key=lambda item: -item[1]):
        bar = "█" * round(24 * total / top)
        print(f"  {region:<{width}}  {total:>9.2f}  {bar}")


if __name__ == "__main__":
    main()
""".trimStart()

private val SALES_CSV = """
date,region,product,amount
2026-01-03,North,Notebook,120.50
2026-01-05,South,Pen,35.00
2026-01-09,East,Backpack,240.00
2026-01-12,West,Notebook,99.90
2026-01-15,North,Pen,42.25
2026-01-19,South,Backpack,310.00
2026-01-22,East,Notebook,88.40
2026-01-26,West,Pen,27.75
2026-01-29,North,Backpack,199.99
2026-02-02,South,Notebook,150.00
""".trimStart()

private val SERVER = """
""${'"'}A tiny JSON API on http://127.0.0.1:8000 using http.server.

Run it, then open http://127.0.0.1:8000/todos in your phone's browser.
It keeps running in the background (see the notification) until you stop it.
""${'"'}
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TODOS = [{"id": 1, "title": "Try PythonX", "done": True}, {"id": 2, "title": "Build something", "done": False}]


class Handler(BaseHTTPRequestHandler):
    def send_json(self, status, body):
        data = json.dumps(body, indent=2).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path in ("/", "/todos"):
            self.send_json(200, TODOS)
        else:
            self.send_json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/todos":
            return self.send_json(404, {"error": "not found"})
        length = int(self.headers.get("Content-Length", 0))
        item = json.loads(self.rfile.read(length) or b"{}")
        todo = {"id": len(TODOS) + 1, "title": item.get("title", "untitled"), "done": False}
        TODOS.append(todo)
        self.send_json(201, todo)


if __name__ == "__main__":
    server = ThreadingHTTPServer(("127.0.0.1", 8000), Handler)
    print("Serving on http://127.0.0.1:8000  (Ctrl-C to stop)")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")
""".trimStart()

private val TESTS_MAIN = """
def slugify(text):
    ""${'"'}Turns "Hello, World!" into "hello-world".""${'"'}
    words = "".join(c.lower() if c.isalnum() else " " for c in text).split()
    return "-".join(words)


def is_palindrome(text):
    letters = [c.lower() for c in text if c.isalnum()]
    return letters == letters[::-1]


if __name__ == "__main__":
    print(slugify("Hello, World!"))
    print(is_palindrome("A man, a plan, a canal: Panama"))
""".trimStart()

private val TESTS_TEST = """
""${'"'}Run this file to run the tests.""${'"'}
import unittest

from main import is_palindrome, slugify


class SlugifyTest(unittest.TestCase):
    def test_basic(self):
        self.assertEqual(slugify("Hello, World!"), "hello-world")

    def test_spaces(self):
        self.assertEqual(slugify("  many   spaces  "), "many-spaces")


class PalindromeTest(unittest.TestCase):
    def test_sentence(self):
        self.assertTrue(is_palindrome("A man, a plan, a canal: Panama"))

    def test_not_palindrome(self):
        self.assertFalse(is_palindrome("PythonX"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
""".trimStart()

val TEMPLATES = listOf(
    Template("Hello World", "The smallest useful program: read input, print a greeting.", mapOf("main.py" to HELLO), listOf("Beginner")),
    Template("Empty project", "Just a main.py, ready for your code.", mapOf("main.py" to "\n"), listOf("Blank")),
    Template("Number guessing game", "Loops, conditions and random numbers in a small interactive game.", mapOf("main.py" to GUESS), listOf("Beginner", "Game")),
    Template("Calculator", "Parses arithmetic with the ast module; a safe alternative to eval().", mapOf("main.py" to CALCULATOR), listOf("Beginner")),
    Template("Command-line tool", "Subcommands, flags and help text with argparse.", mapOf("main.py" to CLI, "notes.txt" to NOTES_TXT), listOf("CLI", "Automation")),
    Template("SQLite notes", "A notes app backed by a real SQLite database file.", mapOf("main.py" to NOTES), listOf("Database")),
    Template("CSV analysis", "Load a CSV file and print statistics and a bar chart.", mapOf("main.py" to CSV_MAIN, "sales.csv" to SALES_CSV), listOf("Data")),
    Template("Weather API client", "Call a JSON web API with urllib and format the result.", mapOf("main.py" to WEATHER), listOf("Web", "Network")),
    Template("Web scraper", "Fetch a page and list its links with html.parser.", mapOf("main.py" to SCRAPER), listOf("Web", "Network")),
    Template("Local JSON API", "A small REST server you can open in the phone's browser.", mapOf("main.py" to SERVER), listOf("Web", "Server")),
    Template("Unit tests", "Functions plus a unittest suite; run test_main.py to test.", mapOf("main.py" to TESTS_MAIN, "test_main.py" to TESTS_TEST),
        listOf("Testing"), entry = "test_main.py"),
)

fun templateByName(name: String) = TEMPLATES.first { it.name == name }
