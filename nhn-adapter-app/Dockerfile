FROM eclipse-temurin:25-jre

# Run as a non-root user. The numeric id lets Kubernetes verify runAsNonRoot.
RUN useradd --system --uid 10001 --no-create-home --shell /usr/sbin/nologin app

COPY target/efm-nhn-adapter-app.jar app.jar

USER 10001

ENTRYPOINT ["java", "-jar", "app.jar"]
