"""The draw breadcrumb survives abrupt child exit without a flush or log pipe."""
from pathlib import Path
import os
import struct
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]

class NativeDrawTraceTest(unittest.TestCase):
    def test_mapped_record_captures_driver_entry_and_normal_return(self):
        with tempfile.TemporaryDirectory() as tmp:
            home=Path(tmp)
            src=home/"probe.c"
            src.write_text('''#include <stdint.h>
typedef struct {int enabled,size,type,stride; unsigned real_buffer; void *pointer; int normalized;} vertexattrib_t;
struct {unsigned program; vertexattrib_t vertexattrib[16];} hw={.program=29};
struct state {typeof(hw)*gleshard; struct {unsigned index;} bind_buffer;} state={.gleshard=&hw,.bind_buffer={.index=0}};
struct state *glstate=&state;
struct {int maxvattrib;} hardext={16};
#include "wurm_draw_trace.h"
int main(int argc, char**argv) {
 hw.vertexattrib[2]=(vertexattrib_t){1,3,0x1406,12,0,(void*)(uintptr_t)0x1234567887654321ULL,0};
 wurm_trace_begin(2,4,0,306,0x1403,(void*)(uintptr_t)0x7a12340000ULL);
 if(argc>1) wurm_trace_end();
 _exit(argc>1?0:139);
}''')
            exe=home/"probe"
            subprocess.run(["gcc","-std=gnu99","-O2","-I"+str(ROOT/"graphics-compat/native"),str(src),"-o",str(exe)],check=True)
            for completed in [False,True]:
                trace=home/"trace.bin"
                r=subprocess.run([str(exe)]+(["done"] if completed else []),env=dict(os.environ,WURM_GL_DRAW_TRACE=str(trace)))
                self.assertEqual(r.returncode,0 if completed else 139)
                data=trace.read_bytes()
                self.assertEqual(len(data),1024)
                w=struct.unpack("<256I",data)
                self.assertEqual(w[:9],(0x57444754,1,2 if completed else 1,1,2,4,0,306,0x1403))
                self.assertEqual(w[9] | (w[10]<<32),0x7a12340000)
                self.assertEqual(w[11:14],(29,0,16))
                k=16+2*8
                self.assertEqual(w[k:k+5],(1,3,0x1406,12,0))
                self.assertEqual(w[k+5] | (w[k+6]<<32),0x1234567887654321)
            # Diagnostics are optional: an unwritable path must not break rendering.
            r=subprocess.run([str(exe),"done"],env=dict(os.environ,WURM_GL_DRAW_TRACE=str(home/"missing"/"trace.bin")))
            self.assertEqual(r.returncode,0)

if __name__ == "__main__": unittest.main()
