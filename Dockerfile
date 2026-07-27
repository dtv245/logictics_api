# syntax=docker/dockerfile:1.7

# ── Stage 1: build ───────────────────────────────────────────────────────────
# A JDK image, because the build needs javac, MapStruct and Lombok annotation
# processing. Nothing from this stage reaches the final image except the jar.
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build

# Copy only what resolves dependencies first, so editing a source file does not
# invalidate the dependency layer. checkstyle.xml and spotbugs-exclude.xml come
# along because the quality plugins are bound to validate/compile and Maven
# reads their configuration while wiring the build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml checkstyle.xml spotbugs-exclude.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp -q \
      -Dcheckstyle.skip=true -Dspotbugs.skip=true \
      dependency:go-offline

COPY src/ src/
# Tests are not run here. They need Postgres and Redis, which do not exist
# during an image build; `mvn verify` on the host or in CI is what gates a
# release. Skipping them here does not skip them from the pipeline.
RUN ./mvnw -B -ntp -q -DskipTests package \
    && mv target/*.jar app.jar

# ── Stage 2: runtime ─────────────────────────────────────────────────────────
# JRE only: no compiler, no Maven, no build cache, and a much smaller attack
# surface than carrying the JDK into production.
FROM eclipse-temurin:21-jre-noble AS runtime

# curl is used by the container healthcheck below; without it Docker cannot tell
# a booting container from a wedged one.
RUN apt-get update \
    && apt-get install --no-install-recommends -y curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root account. A container process that does not need to write
# outside its own workdir has no reason to be able to.
RUN groupadd --system --gid 1001 logistics \
    && useradd --system --uid 1001 --gid logistics --home /app logistics

WORKDIR /app
COPY --from=build --chown=logistics:logistics /build/app.jar app.jar

# Document storage default from FileSystemDocumentStorage (app.documents.storage-root).
RUN mkdir -p /app/data/documents && chown -R logistics:logistics /app/data

USER logistics

EXPOSE 8080

# MaxRAMPercentage instead of a fixed -Xmx: the JVM then respects whatever
# memory limit the container is given, so the same image is correct on a laptop
# and on a memory-capped host.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"
ENV SERVER_PORT=8080

# Uses the app's own liveness endpoint, which is permitAll in SecurityConfiguration.
# start-period covers Flyway migrating a cold database on first boot.
HEALTHCHECK --interval=15s --timeout=5s --start-period=90s --retries=5 \
  CMD curl -fsS "http://localhost:${SERVER_PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
