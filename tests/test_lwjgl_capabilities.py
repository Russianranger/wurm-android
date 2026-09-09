"""The pinned capability adapter must change returns, never lookup/cache behavior."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('capability_builder', ROOT/'scripts/build-lwjgl-api.py')
builder = importlib.util.module_from_spec(spec)
spec.loader.exec_module(builder)


class CapabilityPatchTest(unittest.TestCase):
    def test_exact_methods_keep_lookup_body_and_use_computed_availability(self):
        signatures = [
            'public static boolean checkFunctions(FunctionProvider provider, PointerBuffer caps, int[] indices, String... functions)',
            'public static boolean checkFunctions(FunctionProvider provider, long[] caps, int[] indices, String... functions)',
            'public static boolean reportMissing(String api, String extension)',
        ]
        marker = 'return true; // otherwise the lookup chain will be broken (ANGLE renderer)'
        parts = []
        expected = []
        for i, signature in enumerate(signatures):
            prefix = signature + ' {\n        // authored lookup/cache sentinel ' + str(i) + '\n        '
            parts.append(prefix + marker + '\n    }\n')
            expected.append(prefix + ('return available;' if i < 2 else 'return false;') + '\n    }\n')
        source = ''.join(parts)
        self.assertEqual(builder.patch_capability_checks(source), ''.join(expected))
        with self.assertRaises(ValueError):
            builder.patch_capability_checks(source.replace(marker, 'return true;', 1))
        with self.assertRaises(ValueError):
            builder.patch_capability_checks(source.replace('long[] caps', 'int[] caps'))
        with self.assertRaises(ValueError):
            builder.patch_capability_checks(source + marker)
