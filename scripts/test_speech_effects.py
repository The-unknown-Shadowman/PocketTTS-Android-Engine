#!/usr/bin/env python3
"""Host regression: verify independent duration and pitch on streamed sine waves."""
import ctypes as c
import math
from pathlib import Path
import subprocess
import tempfile
import unittest


class SpeechEffectsTest(unittest.TestCase):
    def test_allocation_sizes_use_size_t(self):
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as temp:
            source = Path(temp) / "allocation.c"
            source.write_text(r'''
#include <assert.h>
#include <stddef.h>
#include <stdlib.h>
static size_t observed;
static void* tracked_realloc(void* pointer, size_t bytes) {
    observed = bytes;
    return NULL; /* Check requested sizes without allocating huge buffers. */
}
#define realloc tracked_realloc
#include "sonic.c"
#undef realloc
int main(void) {
    sonicRealloc(NULL, 0, INT_MAX, 2);
    assert(observed == (size_t)INT_MAX * 2);
    observed = 0;
    sonicRealloc(NULL, 0, -1, 2);
    assert(observed == 0);
    sonicRealloc(NULL, 0, 8, 0);
    assert(observed == 0);
    sonicRealloc(NULL, 0, INT_MAX, INT_MAX);
    if ((size_t)INT_MAX <= SIZE_MAX / (size_t)INT_MAX)
        assert(observed == (size_t)INT_MAX * (size_t)INT_MAX);
    else assert(observed == 0);
    return 0;
}
''')
            executable = Path(temp) / "allocation-test"
            subprocess.run(["cc", "-fsanitize=undefined", "-fno-sanitize-recover=all",
                            "-I", str(root / "vendor/sonic"), str(source), "-lm", "-o", str(executable)], check=True)
            subprocess.run([str(executable)], check=True)

    def test_streamed_duration_and_pitch(self):
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as temp:
            library = Path(temp) / "sonic.so"
            subprocess.run(["cc", "-shared", "-fPIC", str(root / "vendor/sonic/sonic.c"),
                            "-lm", "-o", str(library)], check=True)
            sonic = c.CDLL(str(library))
            sonic.sonicCreateStream.argtypes = [c.c_int, c.c_int]
            sonic.sonicCreateStream.restype = c.c_void_p
            sonic.sonicDestroyStream.argtypes = [c.c_void_p]
            for name in ["sonicSetSpeed", "sonicSetPitch"]:
                getattr(sonic, name).argtypes = [c.c_void_p, c.c_float]
            for name in ["sonicWriteFloatToStream", "sonicReadFloatFromStream"]:
                getattr(sonic, name).argtypes = [c.c_void_p, c.POINTER(c.c_float), c.c_int]
            sonic.sonicFlushStream.argtypes = [c.c_void_p]
            sonic.sonicSamplesAvailable.argtypes = [c.c_void_p]
            for speed, pitch in [(1, 1), (2, 1), (0.5, 1), (1, 2), (1, 0.5), (1.5, 1.5)]:
                with self.subTest(speed=speed, pitch=pitch):
                    stream = sonic.sonicCreateStream(24000, 1)
                    self.assertTrue(stream)
                    try:
                        sonic.sonicSetSpeed(stream, speed)
                        sonic.sonicSetPitch(stream, pitch)
                        output = []

                        def drain():
                            count = sonic.sonicSamplesAvailable(stream)
                            if count:
                                buf = (c.c_float * count)()
                                read = sonic.sonicReadFloatFromStream(stream, buf, count)
                                output.extend(buf[:read])

                        for offset in range(0, 24000, 613):
                            count = min(613, 24000 - offset)
                            buf = (c.c_float * count)(*[0.4 * math.sin(2 * math.pi * 200 * (offset + i) / 24000)
                                                       for i in range(count)])
                            self.assertEqual(1, sonic.sonicWriteFloatToStream(stream, buf, count))
                            drain()
                        self.assertEqual(1, sonic.sonicFlushStream(stream))
                        drain()
                        self.assertLess(abs(len(output) - 24000 / speed), 800)
                        sample = output[2000:-2000]
                        crossings = sum(a <= 0 < b for a, b in zip(sample, sample[1:]))
                        frequency = crossings * 24000 / len(sample)
                        self.assertLess(abs(frequency - 200 * pitch), 20)
                    finally:
                        sonic.sonicDestroyStream(stream)


if __name__ == "__main__":
    unittest.main()
