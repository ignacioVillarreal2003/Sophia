package com.sophia.documentworkspace.infrastructure.service;

import java.io.InputStream;

public interface VirusScanner {

    enum Verdict {
        CLEAN,
        INFECTED
    }

    record ScanResult(Verdict verdict, String signature) {

        public static ScanResult clean() {
            return new ScanResult(Verdict.CLEAN, null);
        }

        public boolean infected() {
            return verdict == Verdict.INFECTED;
        }
    }

    ScanResult scan(InputStream content, String filename);
}
