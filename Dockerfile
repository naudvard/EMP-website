# The binary is built by the GitHub Actions workflow. Ktor links libcurl and OpenSSL statically: it only needs the
# glibc, zlib and the CA certificates, which distroless base has
FROM --platform=$BUILDPLATFORM busybox:1.37 AS data
RUN mkdir -p /app/data

FROM gcr.io/distroless/base-debian13:nonroot
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
