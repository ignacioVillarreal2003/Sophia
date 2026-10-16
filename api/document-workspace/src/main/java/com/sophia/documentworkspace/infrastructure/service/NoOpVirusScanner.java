package com.sophia.documentworkspace.infrastructure.service;

import java.io.InputStream;

public class NoOpVirusScanner implements VirusScanner {

    @Override
    public ScanResult scan(InputStream content, String filename) {
        return ScanResult.clean();
    }
}
