# Android OpenSSL build dependency

Source: https://github.com/KDAB/android_openssl

Pinned repository commit: `b71f1470962019bd89534a2919f5925f93bc5779`.
OpenSSL version: 3.1.8, as identified by the supplied opensslv.h and build_ssl.sh.
License: Apache-2.0, included as LICENSE.
OpenSSL source: https://github.com/openssl/openssl/tree/openssl-3.1.8

Only common headers, ARM static libcrypto libraries, the upstream rebuild script
and its OpenSSL 3 symbol patch are bundled here. SSL/TLS libraries are not linked.
The upstream build script targets Linux/macOS; see its NDK version configuration.

SHA-256 of unmodified libraries:

- arm64-v8a/libcrypto.a: 530E8E9F1A249F31E358A3A91BE2B07A426841BDB4FB60279F4E22A423376425
- armeabi-v7a/libcrypto.a: 8DF5E68377F74E9987F22320C4196AD764BF0B66CF21EC10153CF7A2EB61D391

Local change: common configuration.h selects SIXTY_FOUR_BIT_LONG for LP64 targets
and THIRTY_TWO_BIT otherwise, matching the word size of the two bundled ABIs.

This is a local interoperability test build. These older prebuilt crypto
libraries need replacing with a maintained OpenSSL release before a production
release. The application's local-network protocol compatibility is untested.
