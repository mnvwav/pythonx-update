// `python3` launcher for PythonX. Packaged as libpythonx.so so Android
// installs it into the app's native library directory, where it may be executed.
#include <Python.h>

int main(int argc, char **argv) {
    return Py_BytesMain(argc, argv);
}
