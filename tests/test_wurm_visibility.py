"""Exercise the actual visibility policy against the inspected engine ABI."""
from pathlib import Path
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

FIXTURE = r'''
package wurm.graphics;
public class VisibilityFixture {
    public enum Support { CORE, EXT, NOT_SUPPORTED }
    public static class Helper {
        public static Support occlusionQueries = Support.CORE;
        public static boolean isOcclusionQueryAvailable() { return occlusionQueries != Support.NOT_SUPPORTED; }
        public static boolean deferred = false;
    }
    public static class Option {
        public String[] options = {"Disabled", "Extension", "Core"};
        private int current = 2;
        public int value() { return current; }
        public boolean disabled() { return current == 0; }
        public void setDisabled() { current = 0; }
    }
    public static class Options {
        public static Option useOcclusionQueries = new Option();
        public static int treeDistance = 3, structureDistance = 3, creatureDistance = 3;
    }
    public static class BrokenOptions {
        public static Option useOcclusionQueries = new Option() {{ options = new String[]{"Core", "Disabled", "Extension"}; }};
    }
    // Inspected Cell behavior: query zero hides the cell, except when the player
    // is inside it or within five metres. Far distance does not override this.
    static boolean renderCell(float metres, boolean inFrustum, int sampleResult) {
        if (!inFrustum || metres > 128) return false;
        boolean queryCulling = Helper.isOcclusionQueryAvailable();
        boolean occluded = queryCulling && sampleResult == 0;
        if (metres < 5) occluded = false;
        return !occluded;
    }
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        check(renderCell(4, true, 0) && !renderCell(6, true, 0));
        // Merely disabling the option does not stop GLHelper's CORE path.
        Options.useOcclusionQueries.setDisabled();
        check(!renderCell(6, true, 0));
        for (Support support : Support.values()) {
            Helper.occlusionQueries = support;
            Options.useOcclusionQueries = new Option();
            WurmVisibility.configure("2.1 gl4es wrapper", Helper.class, Options.class);
            check(!Helper.isOcclusionQueryAvailable() && Options.useOcclusionQueries.disabled());
            check(renderCell(4, true, 0) && renderCell(6, true, 0) && renderCell(100, true, 0));
            check(!renderCell(200, true, 0) && !renderCell(10, false, 0));
            check(Options.treeDistance==3 && Options.structureDistance==3 && Options.creatureDistance==3);
            check(!Helper.deferred);
            WurmVisibility.configure("2.1 gl4es wrapper", Helper.class, Options.class);
            check(Options.useOcclusionQueries.disabled());
        }
        Helper.occlusionQueries = Support.CORE;
        Options.useOcclusionQueries = new Option();
        for (String version : new String[]{null, "4.6 desktop", "2.1 other-renderer"}) {
            try {
                WurmVisibility.configure(version, Helper.class, Options.class);
                throw new AssertionError("unexpected backend accepted");
            } catch (IllegalStateException expected) {}
            check(Helper.occlusionQueries==Support.CORE && Options.useOcclusionQueries.value()==2);
        }
        try {
            WurmVisibility.configure("2.1 gl4es wrapper", Helper.class, BrokenOptions.class);
            throw new AssertionError("changed ABI accepted");
        } catch (IllegalStateException expected) {}
        check(Helper.occlusionQueries==Support.CORE && BrokenOptions.useOcclusionQueries.value()==2);
        System.out.println("VISIBILITY_POLICY_PASS near-boundary visibility, far/frustum limits, backend and ABI guards");
    }
}
'''


class WurmVisibilityTest(unittest.TestCase):
    def test_visibility_policy_prevents_zero_sample_culling_without_changing_distance(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/"VisibilityFixture.java"; path.write_text(FIXTURE)
            compile = subprocess.run(["java", "com.sun.tools.javac.Main", "--release", "17", "-d", tmp,
                str(ROOT/"graphics-compat/window/wurm/graphics/WurmVisibility.java"), str(path)],
                capture_output=True, text=True)
            self.assertEqual(compile.returncode, 0, compile.stdout+compile.stderr)
            run = subprocess.run(["java", "-cp", tmp, "wurm.graphics.VisibilityFixture"], capture_output=True, text=True)
            self.assertEqual(run.returncode, 0, run.stdout+run.stderr)
            self.assertIn("VISIBILITY_POLICY_PASS", run.stdout)


if __name__ == "__main__": unittest.main()
