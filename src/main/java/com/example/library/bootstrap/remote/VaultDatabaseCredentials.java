package com.example.library.bootstrap.remote;

record VaultDatabaseCredentials(
        String username,
        String password,
        String leaseId,
        long leaseDurationSeconds,
        boolean renewable) {
}
