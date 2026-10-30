package com.vulntrend.batch.application.probe;

public class C10Local {

    void m() {
        class Local {
            Local() {
            }
        }
    }

    interface Holder {
        class InIface {
            InIface() {
            }
        }
    }

    record Rec(int a) {
        static class InRec {
            InRec() {
            }
        }
    }
}
