package org.softbuddy.pythonx.project

/** A runnable single-file example; [interactive] ones read input. */
data class Example(val title: String, val category: String, val summary: String, val code: String, val interactive: Boolean = false)

private fun ex(title: String, category: String, summary: String, interactive: Boolean = false, code: () -> String) =
    Example(title, category, summary, code().trimIndent() + "\n", interactive)

val EXAMPLES = listOf(
    // ---------- Basics ----------
    ex("Variables and types", "Basics", "int, float, str, bool, None and type()") {
        """
        age = 30
        height = 1.75
        name = "Ada"
        likes_python = True
        nothing = None

        for value in (age, height, name, likes_python, nothing):
            print(f"{value!r:>8}  is  {type(value).__name__}")

        print()
        print("int + float =", age + height)
        print("str * int   =", name * 3)
        print("int('42')   =", int("42") + 1)
        print("bool('')    =", bool(""), "  bool('x') =", bool("x"))
        """
    },
    ex("If, elif and else", "Basics", "Branching on a number you type", interactive = true) {
        """
        number = int(input("Enter a whole number: "))

        if number < 0:
            print("negative")
        elif number == 0:
            print("zero")
        elif number % 2 == 0:
            print("positive and even")
        else:
            print("positive and odd")

        # A conditional expression picks one of two values.
        size = "big" if abs(number) >= 100 else "small"
        print(f"{number} is a {size} number")
        """
    },
    ex("Loops", "Basics", "for, while, range, break, continue and else") {
        """
        print("Squares:", [n * n for n in range(1, 11)])

        total = 0
        for n in range(1, 101):
            if n % 3 and n % 5:
                continue        # skip numbers not divisible by 3 or 5
            total += n
        print("Sum of multiples of 3 or 5 up to 100:", total)

        # while with break: the first power of 2 above 1000.
        power = 1
        while True:
            power *= 2
            if power > 1000:
                break
        print("First power of 2 above 1000:", power)

        # for ... else runs the else only if the loop didn't break.
        for n in (9, 15, 21, 25):
            if n % 7 == 0:
                print("found a multiple of 7:", n)
                break
        else:
            print("no multiple of 7 found")
        """
    },
    ex("FizzBuzz", "Basics", "The classic interview warm-up") {
        """
        for n in range(1, 31):
            word = ("Fizz" if n % 3 == 0 else "") + ("Buzz" if n % 5 == 0 else "")
            print(word or n)
        """
    },
    // ---------- Text ----------
    ex("String methods", "Text", "Slicing, case, split, join, strip and find") {
        """
        text = "  Python runs on your phone, Python runs everywhere.  "

        clean = text.strip()
        print(repr(clean))
        print(clean.upper())
        print(clean.lower().count("python"), "times 'python'")
        print(clean.replace("Python", "Code"))
        print(clean[:6], "|", clean[-11:], "|", clean[::-1][:10])

        words = clean.rstrip(".").replace(",", "").split()
        print(words)
        print(" - ".join(word.capitalize() for word in words))
        print("phone at index", clean.find("phone"))
        """
    },
    ex("f-strings", "Text", "Formatting numbers, padding, dates and debugging") {
        """
        from datetime import date

        price = 1234.5678
        name = "PythonX"
        print(f"{price:.2f}")         # 2 decimals
        print(f"{price:,.1f}")        # thousands separator
        print(f"{0.4567:.1%}")        # percent
        print(f"[{name:<10}]")        # pad right
        print(f"[{name:>10}]")        # pad left
        print(f"[{name:^10}]")        # centre
        print(f"{255:#x} {255:08b}")  # hex and binary
        print(f"{date(2026, 1, 15):%A %d %B %Y}")
        width = 6
        print(f"{42:{width}d}|")      # nested format spec
        print(f"{price=}")            # debugging: shows the expression too
        """
    },
    ex("Word frequency", "Text", "Count words with collections.Counter") {
        """
        import re
        from collections import Counter

        TEXT = '''
        Beautiful is better than ugly. Explicit is better than implicit.
        Simple is better than complex. Complex is better than complicated.
        Flat is better than nested. Sparse is better than dense.
        Readability counts.
        '''

        words = re.findall(r"[a-z']+", TEXT.lower())
        counts = Counter(words)
        print(len(words), "words,", len(counts), "different\n")
        for word, n in counts.most_common(8):
            print(f"{word:<12} {'#' * n} {n}")
        """
    },
    ex("Caesar cipher", "Text", "Encrypt and decrypt with str.maketrans", interactive = true) {
        """
        import string


        def caesar(text, shift):
            lower = string.ascii_lowercase
            upper = string.ascii_uppercase
            shift %= 26
            table = str.maketrans(lower + upper, lower[shift:] + lower[:shift] + upper[shift:] + upper[:shift])
            return text.translate(table)


        message = input("Message: ") or "Hello, PythonX!"
        shift = int(input("Shift [3]: ") or 3)
        secret = caesar(message, shift)
        print("Encrypted:", secret)
        print("Decrypted:", caesar(secret, -shift))
        """
    },
    // ---------- Collections ----------
    ex("Lists, tuples, sets, dicts", "Collections", "The four built-in containers side by side") {
        """
        fruits = ["apple", "banana", "cherry"]
        fruits.append("date")
        fruits.insert(0, "avocado")
        print("list :", fruits, "| first two:", fruits[:2])

        point = (3, 4)
        x, y = point
        print("tuple:", point, "| unpacked:", x, y)

        a, b = {1, 2, 3, 4}, {3, 4, 5}
        print("set  : union", a | b, "intersection", a & b, "difference", a - b)

        ages = {"Ada": 36, "Linus": 28}
        ages["Grace"] = 85
        ages.setdefault("Ada", 0)          # keeps the existing value
        print("dict :", ages)
        for name, age in sorted(ages.items(), key=lambda item: item[1]):
            print(f"       {name} is {age}")
        print("get with default:", ages.get("Guido", "unknown"))
        """
    },
    ex("Comprehensions", "Collections", "List, dict, set and generator expressions") {
        """
        numbers = range(1, 21)

        evens = [n for n in numbers if n % 2 == 0]
        squares = {n: n * n for n in range(1, 6)}
        remainders = {n % 7 for n in numbers}
        total = sum(n * n for n in numbers)          # a generator: no list in memory
        grid = [[r * c for c in range(1, 4)] for r in range(1, 4)]

        print("evens      ", evens)
        print("squares    ", squares)
        print("remainders ", remainders)
        print("sum of sq  ", total)
        print("grid       ", grid)
        print("flattened  ", [cell for row in grid for cell in row])
        """
    },
    ex("Sorting", "Collections", "sorted with keys, reverse and multiple fields") {
        """
        people = [
            {"name": "Ada", "age": 36, "city": "London"},
            {"name": "Linus", "age": 28, "city": "Helsinki"},
            {"name": "Grace", "age": 85, "city": "New York"},
            {"name": "Alan", "age": 41, "city": "London"},
        ]

        print("by age      :", [p["name"] for p in sorted(people, key=lambda p: p["age"])])
        print("oldest first:", [p["name"] for p in sorted(people, key=lambda p: p["age"], reverse=True)])
        print("city, name  :", [(p["city"], p["name"]) for p in sorted(people, key=lambda p: (p["city"], p["name"]))])
        print("longest name:", max(people, key=lambda p: len(p["name"]))["name"])

        words = ["banana", "Apple", "cherry", "apple"]
        print("case-insensitive:", sorted(words, key=str.lower))
        """
    },
    // ---------- Functions ----------
    ex("Functions and arguments", "Functions", "Defaults, *args, **kwargs and keyword-only") {
        """
        def greet(name, greeting="Hello", *, punctuation="!"):
            return f"{greeting}, {name}{punctuation}"


        def average(*numbers):
            return sum(numbers) / len(numbers) if numbers else 0


        def describe(**fields):
            return ", ".join(f"{key}={value}" for key, value in fields.items())


        print(greet("Ada"))
        print(greet("Linus", "Hi"))
        print(greet("Grace", punctuation="."))
        print(average(3, 4, 5, 6))
        print(describe(language="Python", version=3.14, fun=True))

        values = [10, 20, 30]
        print(average(*values))      # unpack a list into arguments
        """
    },
    ex("Decorators", "Functions", "Timing and caching functions with decorators") {
        """
        import time
        from functools import lru_cache, wraps


        def timed(function):
            @wraps(function)
            def wrapper(*args, **kwargs):
                start = time.perf_counter()
                result = function(*args, **kwargs)
                print(f"{function.__name__}{args} took {(time.perf_counter() - start) * 1000:.2f} ms")
                return result
            return wrapper


        def slow_fib(n):
            return n if n < 2 else slow_fib(n - 1) + slow_fib(n - 2)


        @lru_cache(maxsize=None)
        def fast_fib(n):
            return n if n < 2 else fast_fib(n - 1) + fast_fib(n - 2)


        timed(slow_fib)(25)
        timed(fast_fib)(25)
        print("fib(200) =", fast_fib(200))
        """
    },
    ex("Generators", "Functions", "yield, lazy pipelines and itertools") {
        """
        import itertools


        def primes():
            ""${'"'}An endless stream of prime numbers.""${'"'}
            found = []
            for n in itertools.count(2):
                if all(n % p for p in found if p * p <= n):
                    found.append(n)
                    yield n


        print("first 15 primes:", list(itertools.islice(primes(), 15)))

        # A lazy pipeline: nothing is computed until it is consumed.
        squares = (n * n for n in itertools.count(1))
        odd = (n for n in squares if n % 2)
        print("odd squares under 200:", list(itertools.takewhile(lambda n: n < 200, odd)))

        for a, b in itertools.pairwise("PYTHON"):
            print(a + b, end=" ")
        print()
        """
    },
    // ---------- Classes ----------
    ex("Classes and inheritance", "Classes", "A small shape hierarchy with properties") {
        """
        import math


        class Shape:
            def area(self):
                raise NotImplementedError

            def __str__(self):
                return f"{type(self).__name__} with area {self.area():.2f}"


        class Circle(Shape):
            def __init__(self, radius):
                self.radius = radius

            def area(self):
                return math.pi * self.radius ** 2


        class Rectangle(Shape):
            def __init__(self, width, height):
                self.width = width
                self.height = height

            def area(self):
                return self.width * self.height

            @property
            def is_square(self):
                return self.width == self.height


        shapes = [Circle(1), Rectangle(3, 4), Rectangle(2, 2)]
        for shape in sorted(shapes, key=lambda s: s.area()):
            print(shape)
        print("squares:", [s for s in shapes if isinstance(s, Rectangle) and s.is_square])
        """
    },
    ex("Dataclasses", "Classes", "Less boilerplate for data-holding classes") {
        """
        from dataclasses import dataclass, field


        @dataclass(order=True)
        class Task:
            priority: int
            title: str = field(compare=False)
            tags: list[str] = field(default_factory=list, compare=False)
            done: bool = field(default=False, compare=False)


        tasks = [
            Task(2, "Write tests", ["code"]),
            Task(1, "Fix crash", ["bug", "urgent"]),
            Task(3, "Update docs"),
        ]
        tasks[0].done = True

        for task in sorted(tasks):
            mark = "x" if task.done else " "
            print(f"[{mark}] P{task.priority} {task.title:<12} {', '.join(task.tags)}")
        print(tasks[1])
        """
    },
    // ---------- Files ----------
    ex("Reading and writing files", "Files", "pathlib, text, JSON and CSV in the project folder") {
        """
        import csv
        import json
        from pathlib import Path

        folder = Path(__file__).parent

        notes = folder / "notes.txt"
        notes.write_text("first line\nsecond line\n")
        with notes.open("a") as file:
            file.write("appended line\n")
        print(notes.read_text())

        settings = {"theme": "dark", "font_size": 14, "recent": ["main.py"]}
        (folder / "settings.json").write_text(json.dumps(settings, indent=2))
        print("JSON round trip:", json.loads((folder / "settings.json").read_text()))

        with (folder / "scores.csv").open("w", newline="") as file:
            writer = csv.writer(file)
            writer.writerows([["name", "score"], ["Ada", 92], ["Linus", 87]])
        with (folder / "scores.csv").open(newline="") as file:
            print("CSV rows:", list(csv.DictReader(file)))

        print("\nFiles here:", sorted(p.name for p in folder.iterdir() if p.is_file()))
        """
    },
    ex("Exceptions", "Files", "try, except, else, finally and your own errors") {
        """
        class ValidationError(Exception):
            pass


        def parse_age(text):
            try:
                age = int(text)
            except ValueError:
                raise ValidationError(f"{text!r} is not a number") from None
            if not 0 <= age <= 150:
                raise ValidationError(f"{age} is not a realistic age")
            return age


        for value in ("42", "abc", "200", "7"):
            try:
                age = parse_age(value)
            except ValidationError as error:
                print("invalid:", error)
            else:
                print("valid  :", age)
            finally:
                pass  # runs either way: close files, release locks...

        try:
            open("does-not-exist.txt")
        except FileNotFoundError as error:
            print("\n" + type(error).__name__ + ":", error.strerror)
        """
    },
    // ---------- Algorithms ----------
    ex("Binary search", "Algorithms", "Find an item in a sorted list in O(log n)") {
        """
        import bisect


        def binary_search(items, target):
            low, high = 0, len(items) - 1
            steps = 0
            while low <= high:
                steps += 1
                middle = (low + high) // 2
                if items[middle] == target:
                    return middle, steps
                if items[middle] < target:
                    low = middle + 1
                else:
                    high = middle - 1
            return -1, steps


        numbers = list(range(0, 1_000_000, 3))
        for target in (999_999, 300, 301):
            index, steps = binary_search(numbers, target)
            print(f"{target:>7}: index {index:>7} after {steps} steps")

        # The standard library's bisect does the same.
        print("bisect:", bisect.bisect_left(numbers, 300))
        """
    },
    ex("Sorting algorithms", "Algorithms", "Merge sort and quicksort, timed against sorted()") {
        """
        import random
        import time


        def merge_sort(items):
            if len(items) <= 1:
                return items
            middle = len(items) // 2
            left, right = merge_sort(items[:middle]), merge_sort(items[middle:])
            merged = []
            i = j = 0
            while i < len(left) and j < len(right):
                if left[i] <= right[j]:
                    merged.append(left[i]); i += 1
                else:
                    merged.append(right[j]); j += 1
            return merged + left[i:] + right[j:]


        def quick_sort(items):
            if len(items) <= 1:
                return items
            pivot = random.choice(items)
            return (quick_sort([x for x in items if x < pivot]) + [x for x in items if x == pivot]
                    + quick_sort([x for x in items if x > pivot]))


        data = [random.randint(0, 10_000) for _ in range(20_000)]
        for name, sort in (("merge sort", merge_sort), ("quicksort", quick_sort), ("sorted()", sorted)):
            start = time.perf_counter()
            result = sort(data)
            print(f"{name:<11} {(time.perf_counter() - start) * 1000:7.1f} ms  correct={result == sorted(data)}")
        """
    },
    ex("Maze solver", "Algorithms", "Breadth-first search finds the shortest path") {
        """
        from collections import deque

        MAZE = '''
        ###########
        #S  #     #
        # # # ### #
        # #   #   #
        # ##### # #
        #     # #E#
        ###########
        '''.strip().splitlines()

        grid = [list(row.strip()) for row in MAZE]
        start = next((r, c) for r, row in enumerate(grid) for c, ch in enumerate(row) if ch == "S")
        end = next((r, c) for r, row in enumerate(grid) for c, ch in enumerate(row) if ch == "E")

        previous = {start: None}
        queue = deque([start])
        while queue:
            r, c = queue.popleft()
            if (r, c) == end:
                break
            for nr, nc in ((r + 1, c), (r - 1, c), (r, c + 1), (r, c - 1)):
                if grid[nr][nc] != "#" and (nr, nc) not in previous:
                    previous[(nr, nc)] = (r, c)
                    queue.append((nr, nc))

        step = previous[end]
        length = 1
        while step != start:
            grid[step[0]][step[1]] = "·"
            step = previous[step]
            length += 1
        print("\n".join("".join(row) for row in grid))
        print(f"\nShortest path: {length} steps")
        """
    },
    // ---------- Math ----------
    ex("Prime sieve", "Math", "Sieve of Eratosthenes up to a million") {
        """
        import time


        def sieve(limit):
            is_prime = bytearray([1]) * (limit + 1)
            is_prime[0:2] = b"\x00\x00"
            for n in range(2, int(limit ** 0.5) + 1):
                if is_prime[n]:
                    is_prime[n * n::n] = bytearray(len(range(n * n, limit + 1, n)))
            return [n for n, flag in enumerate(is_prime) if flag]


        start = time.perf_counter()
        primes = sieve(1_000_000)
        print(f"{len(primes):,} primes below 1,000,000 in {(time.perf_counter() - start) * 1000:.0f} ms")
        print("last five:", primes[-5:])
        twins = sum(1 for a, b in zip(primes, primes[1:]) if b - a == 2)
        print(f"twin prime pairs: {twins:,}")
        """
    },
    ex("Statistics and fractions", "Math", "statistics, fractions, decimal and math") {
        """
        import math
        import statistics
        from decimal import Decimal, getcontext
        from fractions import Fraction

        scores = [72, 85, 91, 66, 85, 99, 78, 85, 90]
        print("mean   ", round(statistics.mean(scores), 2))
        print("median ", statistics.median(scores))
        print("mode   ", statistics.mode(scores))
        print("stdev  ", round(statistics.stdev(scores), 2))
        print("quartiles", statistics.quantiles(scores, n=4))

        print("\n0.1 + 0.2 =", 0.1 + 0.2)
        print("Decimal  =", Decimal("0.1") + Decimal("0.2"))
        print("Fraction =", Fraction(1, 3) + Fraction(1, 6))
        getcontext().prec = 40
        print("sqrt(2)  =", Decimal(2).sqrt())
        print("\ncomb(52, 5) =", f"{math.comb(52, 5):,}", "poker hands")
        print("gcd(84, 36) =", math.gcd(84, 36), "  lcm =", math.lcm(84, 36))
        """
    },
    // ---------- Terminal ----------
    ex("Progress bar", "Terminal", "Redraw a line in place with \\r") {
        """
        import sys
        import time

        steps = 40
        for done in range(steps + 1):
            bar = "█" * done + "░" * (steps - done)
            sys.stdout.write(f"\r[{bar}] {done * 100 // steps:3d}%")
            sys.stdout.flush()
            time.sleep(0.05)
        print("\nDone!")
        """
    },
    ex("Colours in the terminal", "Terminal", "ANSI escape codes for colour and style") {
        """
        RESET = "\033[0m"
        styles = {"bold": "1", "dim": "2", "italic": "3", "underline": "4", "reverse": "7"}

        for name, code in styles.items():
            print(f"\033[{code}m{name:<10}{RESET}", end=" ")
        print("\n")

        for base in range(30, 38):
            print(f"\033[{base}m██ {base}{RESET}  \033[{base + 60}m██ {base + 60}{RESET}")

        print()
        for row in range(6):
            line = ""
            for col in range(36):
                color = 16 + row * 36 + col
                line += f"\033[48;5;{color}m "
            print(line + RESET)

        for i in range(0, 256, 8):
            print(f"\033[38;2;{i};{255 - i};180m▌", end="")
        print(RESET)
        """
    },
    ex("Conway's Game of Life", "Terminal", "An animated cellular automaton") {
        """
        import random
        import time

        WIDTH, HEIGHT, FRAMES = 36, 18, 60
        cells = {(x, y) for x in range(WIDTH) for y in range(HEIGHT) if random.random() < 0.3}


        def neighbours(x, y):
            return sum(((x + dx) % WIDTH, (y + dy) % HEIGHT) in cells
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1) if dx or dy)


        for generation in range(FRAMES):
            print("\033[H\033[2J", end="")   # clear the screen
            for y in range(HEIGHT):
                print("".join("█" if (x, y) in cells else " " for x in range(WIDTH)))
            print(f"generation {generation + 1}/{FRAMES}  ·  {len(cells)} alive")
            cells = {(x, y) for x in range(WIDTH) for y in range(HEIGHT)
                     if neighbours(x, y) == 3 or ((x, y) in cells and neighbours(x, y) == 2)}
            time.sleep(0.12)
        """
    },
    // ---------- Date and time ----------
    ex("Dates and times", "Date & time", "datetime, timedelta, time zones and calendars") {
        """
        import calendar
        from datetime import date, datetime, timedelta
        from zoneinfo import ZoneInfo

        now = datetime.now(ZoneInfo("UTC"))
        print("UTC now      ", now.strftime("%Y-%m-%d %H:%M"))
        for zone in ("Asia/Kolkata", "Europe/London", "America/New_York", "Asia/Tokyo"):
            print(f"{zone:<13}", now.astimezone(ZoneInfo(zone)).strftime("%a %H:%M"))

        today = date.today()
        new_year = date(today.year + 1, 1, 1)
        print("\nDays until new year:", (new_year - today).days)
        print("In 100 days it will be", (today + timedelta(days=100)).strftime("%A %d %B"))
        print("Parsed:", datetime.strptime("2026-03-14 15:09", "%Y-%m-%d %H:%M"))

        print()
        print(calendar.month(today.year, today.month))
        """
    },
    // ---------- Concurrency ----------
    ex("Threads and asyncio", "Concurrency", "Run waits in parallel instead of one by one") {
        """
        import asyncio
        import time
        from concurrent.futures import ThreadPoolExecutor


        def blocking_job(n):
            time.sleep(0.5)
            return n * n


        start = time.perf_counter()
        with ThreadPoolExecutor(max_workers=5) as pool:
            results = list(pool.map(blocking_job, range(5)))
        print(f"threads : {results} in {time.perf_counter() - start:.2f}s (not 2.5s)")


        async def async_job(n):
            await asyncio.sleep(0.5)
            return n * 10


        async def main():
            start = time.perf_counter()
            results = await asyncio.gather(*(async_job(n) for n in range(5)))
            print(f"asyncio : {results} in {time.perf_counter() - start:.2f}s")


        asyncio.run(main())
        """
    },
)

val EXAMPLE_CATEGORIES = EXAMPLES.map { it.category }.distinct()
