# syntax=docker/dockerfile:1.4

# ---- Build stage ----
FROM registry.access.redhat.com/ubi9/openjdk-17:1.20 AS build

USER 0

WORKDIR /build

# Copy Maven project files first so dependency resolution can be cached
COPY pom.xml .

# Copy source and other build inputs
COPY src/ src/
COPY openapi/ openapi/

# Build the executable Spring Boot JAR.
#
# Resolve through the settings.xml the CI pipeline leaves in the build context
# instead of going straight to Maven Central.
#
# Why this is needed: ci-build-publish compiles this source once with the
# Tekton `maven` task, which resolves through the in-cluster Artifactory
# virtual repo, and then hands the same workspace to buildah -- which compiles
# it a SECOND time, here. A bare `mvn clean package` only knows about Central,
# so the moment main pins a Lightwell backport (3.14.0.rhlw-00001 and friends,
# which exist only in Artifactory) the pipeline passes its `package` task and
# then fails in this stage on a missing artifact.
#
# Bind-mounted rather than COPYed, for two reasons:
#   * that settings.xml carries the Artifactory credential, and a mount leaves
#     no layer holding it -- not even in this discarded build stage;
#   * mounting the context root means there is no COPY of an optional file to
#     go wrong when it is absent.
#
# The path is where the `maven` task's maven-generate step writes it:
# <workspace>/<subdirectory>/maven-generate/settings.xml, and buildah's build
# context is that same <subdirectory>. When it is absent -- a plain
# `podman build` of a fresh clone -- this falls back to Central, so the
# Containerfile stays buildable outside the pipeline.
#
# -DskipTests stays: the pipeline's `package` task already ran them, and a
# second run here would only slow the build down.
RUN --mount=type=bind,target=/ctx \
    if [ -f /ctx/maven-generate/settings.xml ]; then \
      echo "Resolving through settings.xml from the build context"; \
      mvn -B -s /ctx/maven-generate/settings.xml clean package -DskipTests; \
    else \
      echo "No settings.xml in the build context; resolving from Maven Central"; \
      mvn -B clean package -DskipTests; \
    fi


# ---- Runtime stage ----
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.20

WORKDIR /deployments

COPY --from=build /build/target/spring-boot-lw-poc-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

USER 185

ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]
