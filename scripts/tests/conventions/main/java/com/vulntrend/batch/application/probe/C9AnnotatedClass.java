package com.vulntrend.batch.application.probe;

import lombok.Getter;

public class C9AnnotatedClass {

    @Getter static class Q {
        Q() {
        }
    }

    @SuppressWarnings("unused") private static final class R {
        R() {
        }
    }
}
