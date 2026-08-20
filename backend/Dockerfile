FROM gradle:jdk21 AS build
WORKDIR /home/gradle/project
COPY --chown=gradle:gradle . .
RUN gradle --no-daemon clean installDist

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && curl -fsSL -o /usr/local/share/ca-certificates/russian_trusted_root_ca.crt \
        "https://gu-st.ru/content/lending/russian_trusted_root_ca_pem.crt" \
    && curl -fsSL -o /usr/local/share/ca-certificates/russian_trusted_sub_ca.crt \
        "https://gu-st.ru/content/lending/russian_trusted_sub_ca_pem.crt" \
    && update-ca-certificates \
    && keytool -importcert -noprompt -trustcacerts \
        -alias russian-trusted-root \
        -file /usr/local/share/ca-certificates/russian_trusted_root_ca.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && keytool -importcert -noprompt -trustcacerts \
        -alias russian-trusted-sub \
        -file /usr/local/share/ca-certificates/russian_trusted_sub_ca.crt \
        -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /home/gradle/project/build/install/backend/ ./
EXPOSE 8080
ENTRYPOINT ["./bin/backend"]
