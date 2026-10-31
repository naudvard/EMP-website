# The binary is built by the GitHub Actions workflow. Ktor links libcurl and OpenSSL statically: it only needs the
# glibc, libgcc_s, libcrypt, zlib and the CA certificates
FROM --platform=$BUILDPLATFORM busybox:1.37 AS data
RUN mkdir -p /app/data

# Kotlin/Native binaries need libcrypt.so.1, split out of glibc and absent from distroless debian13.
# No RUN here: only the arm64 layers are pulled, no emulation needed.
FROM debian:trixie-slim AS libs

# The cc variant is distroless base plus libgcc_s
FROM gcr.io/distroless/cc-debian13:nonroot
COPY --from=libs /usr/lib/aarch64-linux-gnu/libcrypt.so.1* /usr/lib/aarch64-linux-gnu/
WORKDIR /app
COPY --from=data --chown=nonroot:nonroot /app/data ./data
COPY build/bin/linuxArm64/releaseExecutable/emp.kexe ./emp
COPY application.yaml ./
# libcurl falls back on OpenSSL's default paths, where distroless has no hashed certificates directory
ENV SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt
ENV HOST=0.0.0.0
EXPOSE 3000
VOLUME /app/data
ENTRYPOINT ["/app/emp"]