/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * License); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an AS IS BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  base
  // This plugin provides a task to determine which dependencies have updates.
  // Additionally, the plugin checks for updates to Gradle itself.
  //
  // See https://github.com/ben-manes/gradle-versions-plugin for further details.
  id("com.github.ben-manes.versions") version "0.33.0"
  // Apply one top level rat plugin to perform any required license enforcement analysis
  id("org.nosphere.apache.rat") version "0.7.0"
  // Enable gradle-based release management
  id("net.researchgate.release") version "2.8.1"
  id("org.apache.beam.module")
  id("org.sonarqube") version "3.0"
}

/*************************************************************************************************/
// Configure the root project

tasks.rat {
  // Set input directory to that of the root project instead of the CWD. This
  // makes .gitignore rules (added below) work properly.
  inputDir.set(project.rootDir)

  val exclusions = mutableListOf(
    // Ignore files we track but do not distribute
    "**/.github/**/*",
    "**/.gitkeep",
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.properties",

    "**/package-list",
    "**/test.avsc",
    "**/user.avsc",
    "**/test/resources/**/*.txt",
    "**/test/resources/**/*.csv",
    "**/test/**/.placeholder",

    // Default eclipse excludes neglect subprojects

    // Proto/grpc generated wrappers
    "**/apache_beam/portability/api/*_pb2*.py",
    "**/go/pkg/beam/**/*.pb.go",

    // Ignore go.sum files, which don't permit headers
    "**/go.sum",

    // Ignore Go test data files
    "**/go/data/**",

    // VCF test files
    "**/apache_beam/testing/data/vcf/*",

    // JDBC package config files
    "**/META-INF/services/java.sql.Driver",

    // Website build files
    "**/Gemfile.lock",
    "**/Rakefile",
    "**/.htaccess",
    "website/www/site/assets/scss/_bootstrap.scss",
    "website/www/site/assets/scss/bootstrap/**/*",
    "website/www/site/assets/js/**/*",
    "website/www/site/static/images/mascot/*.ai",
    "website/www/site/static/js/bootstrap*.js",
    "website/www/site/static/js/bootstrap/**/*",
    "website/www/site/themes",
    "website/www/yarn.lock",
    "website/www/package.json",
    "website/www/site/static/js/hero/lottie-light.min.js",
    "website/www/site/static/js/keen-slider.min.js",
    "website/www/site/assets/scss/_keen-slider.scss",

    // Ignore ownership files
    "ownership/**/*",
    "**/OWNERS",

    // Ignore CPython LICENSE file
    "LICENSE.python",

    // Json doesn't support comments.
    "**/*.json",

    // Katas files
    "learning/katas/**/course-remote-info.yaml",
    "learning/katas/**/section-remote-info.yaml",
    "learning/katas/**/lesson-remote-info.yaml",
    "learning/katas/**/task-remote-info.yaml",
    "learning/katas/**/*.txt",

    // test p8 file for SnowflakeIO
    "sdks/java/io/snowflake/src/test/resources/invalid_test_rsa_key.p8",
    "sdks/java/io/snowflake/src/test/resources/valid_test_rsa_key.p8",

    // Mockito extensions
    "sdks/java/io/amazon-web-services2/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker",
    "sdks/java/io/azure/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker",
    "sdks/java/extensions/ml/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker",

    // JupyterLab extensions
    "sdks/python/apache_beam/runners/interactive/extensions/apache-beam-jupyterlab-sidepanel/yarn.lock",

    // Autogenerated apitools clients.
    "sdks/python/apache_beam/runners/dataflow/internal/clients/*/**/*.py",

    // Sample text file for Java quickstart
    "sdks/java/maven-archetypes/examples/sample.txt",

    // Ignore Flutter autogenerated files for Playground
    "playground/frontend/.metadata",
    "playground/frontend/pubspec.lock",

    // Ignore .gitkeep file
    "**/.gitkeep",

    // Ignore Flutter localization .arb files (doesn't support comments)
    "playground/frontend/lib/l10n/**/*.arb",

    // Ignore LICENSES copied onto containers
    "sdks/java/container/license_scripts/manual_licenses",
    "sdks/python/container/license_scripts/manual_licenses"
  )

  // Add .gitignore excludes to the Apache Rat exclusion list. We re-create the behavior
  // of the Apache Maven Rat plugin since the Apache Ant Rat plugin doesn't do this
  // automatically.
  val gitIgnore = project(":").file(".gitignore")
  if (gitIgnore.exists()) {
    val gitIgnoreExcludes = gitIgnore.readLines().filter { it.isNotEmpty() && !it.startsWith("#") }
    exclusions.addAll(gitIgnoreExcludes)
  }

  failOnError.set(true)
  setExcludes(exclusions)
}
tasks.check.get().dependsOn(tasks.rat)

// Define root pre/post commit tasks simplifying what is needed
// to be specified on the commandline when executing locally.
// This indirection also makes Jenkins use the branch of the PR
// for the test definitions.
tasks.register("javaPreCommit") {
  // We need to list the model/* builds since sdks/java/core doesn't
  // depend on any of the model.
  dependsOn(":model:pipeline:build")
  dependsOn(":model:job-management:build")
  dependsOn(":model:fn-execution:build")
  dependsOn(":runners:google-cloud-dataflow-java:worker:legacy-worker:build")
  dependsOn(":sdks:java:core:buildNeeded")
  dependsOn(":sdks:java:core:buildDependents")
  dependsOn(":examples:java:preCommit")
  dependsOn(":examples:java:twitter:preCommit")
  dependsOn(":sdks:java:extensions:sql:jdbc:preCommit")
  dependsOn(":sdks:java:javadoc:allJavadoc")
  dependsOn(":runners:direct-java:needsRunnerTests")
  dependsOn(":sdks:java:container:java8:docker")
}

tasks.register("sqlPreCommit") {
  dependsOn(":sdks:java:extensions:sql:runBasicExample")
  dependsOn(":sdks:java:extensions:sql:runPojoExample")
  dependsOn(":sdks:java:extensions:sql:build")
  dependsOn(":sdks:java:extensions:sql:buildDependents")
}

tasks.register("javaPreCommitPortabilityApi") {
  dependsOn(":runners:google-cloud-dataflow-java:worker:build")
}

tasks.register("javaPostCommit") {
  dependsOn(":sdks:java:extensions:google-cloud-platform-core:postCommit")
  dependsOn(":sdks:java:extensions:zetasketch:postCommit")
  dependsOn(":sdks:java:io:debezium:integrationTest")
  dependsOn(":sdks:java:io:jdbc:integrationTest")
  dependsOn(":sdks:java:io:google-cloud-platform:postCommit")
  dependsOn(":sdks:java:io:kinesis:integrationTest")
  dependsOn(":sdks:java:io:amazon-web-services:integrationTest")
  dependsOn(":sdks:java:io:amazon-web-services2:integrationTest")
  dependsOn(":sdks:java:extensions:ml:postCommit")
  dependsOn(":sdks:java:io:kafka:kafkaVersionsCompatibilityTest")
  dependsOn(":sdks:java:io:neo4j:integrationTest")
}

tasks.register("javaHadoopVersionsTest") {
  dependsOn(":sdks:java:io:hadoop-common:hadoopVersionsTest")
  dependsOn(":sdks:java:io:hadoop-file-system:hadoopVersionsTest")
  dependsOn(":sdks:java:io:hadoop-format:hadoopVersionsTest")
  dependsOn(":sdks:java:io:hcatalog:hadoopVersionsTest")
  dependsOn(":sdks:java:io:parquet:hadoopVersionsTest")
  dependsOn(":sdks:java:extensions:sorter:hadoopVersionsTest")
  dependsOn(":runners:spark:2:hadoopVersionsTest")
}

tasks.register("sqlPostCommit") {
  dependsOn(":sdks:java:extensions:sql:postCommit")
  dependsOn(":sdks:java:extensions:sql:jdbc:postCommit")
  dependsOn(":sdks:java:extensions:sql:datacatalog:postCommit")
  dependsOn(":sdks:java:extensions:sql:hadoopVersionsTest")
}

tasks.register("goPreCommit") {
  // Ensure the Precommit builds run after the tests, in order to avoid the
  // flake described in BEAM-11918. This is done by splitting them into two
  // tasks and using "mustRunAfter" to enforce ordering.
  dependsOn(":goPrecommitTest")
  dependsOn(":goPrecommitBuild")
}

tasks.register("goPrecommitTest") {
  dependsOn(":sdks:go:goTest")
}

tasks.register("goPrecommitBuild") {
  mustRunAfter(":goPrecommitTest")

  dependsOn(":sdks:go:goBuild")
  dependsOn(":sdks:go:examples:goBuild")
  dependsOn(":sdks:go:test:goBuild")

  // Ensure all container Go boot code builds as well.
  dependsOn(":sdks:java:container:goBuild")
  dependsOn(":sdks:python:container:goBuild")
  dependsOn(":sdks:go:container:goBuild")
}

tasks.register("goPortablePreCommit") {
  dependsOn(":sdks:go:test:ulrValidatesRunner")
}

tasks.register("goPostCommit") {
  dependsOn(":goIntegrationTests")
}

tasks.register("goIntegrationTests") {
  doLast {
    exec {
      executable("sh")
      args("-c", "./sdks/go/test/run_validatesrunner_tests.sh --runner dataflow")
    }
  }
  dependsOn(":sdks:go:test:build")
  dependsOn(":runners:google-cloud-dataflow-java:worker:shadowJar")
}

tasks.register("playgroundPreCommit") {
  dependsOn(":playground:lintProto")
  dependsOn(":playground:backend:precommit")
  dependsOn(":playground:frontend:precommit")
}

tasks.register("pythonPreCommit") {
  dependsOn(":sdks:python:test-suites:tox:pycommon:preCommitPyCommon")
  dependsOn(":sdks:python:test-suites:tox:py36:preCommitPy36")
  dependsOn(":sdks:python:test-suites:tox:py37:preCommitPy37")
  dependsOn(":sdks:python:test-suites:tox:py38:preCommitPy38")
  dependsOn(":sdks:python:test-suites:tox:py39:preCommitPy39")
  dependsOn(":sdks:python:test-suites:dataflow:preCommitIT")
  dependsOn(":sdks:python:test-suites:dataflow:preCommitIT_V2")
}

tasks.register("pythonDocsPreCommit") {
  dependsOn(":sdks:python:test-suites:tox:pycommon:docs")
}

tasks.register("pythonDockerBuildPreCommit") {
  dependsOn(":sdks:python:container:py36:docker")
  dependsOn(":sdks:python:container:py37:docker")
  dependsOn(":sdks:python:container:py38:docker")
  dependsOn(":sdks:python:container:py39:docker")
}

tasks.register("pythonLintPreCommit") {
  // TODO(BEAM-9980): Find a better way to specify lint and formatter tasks without hardcoding py version.
  dependsOn(":sdks:python:test-suites:tox:py37:lint")
}

tasks.register("pythonFormatterPreCommit") {
  dependsOn("sdks:python:test-suites:tox:py38:formatter")
}

tasks.register("python36PostCommit") {
  dependsOn(":sdks:python:test-suites:dataflow:py36:postCommitIT")
  dependsOn(":sdks:python:test-suites:direct:py36:postCommitIT")
  dependsOn(":sdks:python:test-suites:portable:py36:postCommitPy36")
}

tasks.register("python37PostCommit") {
  dependsOn(":sdks:python:test-suites:dataflow:py37:postCommitIT")
  dependsOn(":sdks:python:test-suites:direct:py37:postCommitIT")
  dependsOn(":sdks:python:test-suites:direct:py37:directRunnerIT")
  dependsOn(":sdks:python:test-suites:direct:py37:hdfsIntegrationTest")
  dependsOn(":sdks:python:test-suites:direct:py37:mongodbioIT")
  dependsOn(":sdks:python:test-suites:portable:py37:postCommitPy37")
  dependsOn(":sdks:python:test-suites:dataflow:py37:spannerioIT")
  dependsOn(":sdks:python:test-suites:direct:py37:spannerioIT")
  dependsOn(":sdks:python:test-suites:portable:py37:xlangSpannerIOIT")
}

tasks.register("python38PostCommit") {
  dependsOn(":sdks:python:test-suites:dataflow:py38:postCommitIT")
  dependsOn(":sdks:python:test-suites:direct:py38:postCommitIT")
  dependsOn(":sdks:python:test-suites:direct:py38:hdfsIntegrationTest")
  dependsOn(":sdks:python:test-suites:portable:py38:postCommitPy38")
}

tasks.register("python39PostCommit") {
  // TODO(BEAM-12920): Enable DF suite here.
  // dependsOn(":sdks:python:test-suites:dataflow:py39:postCommitIT")
  dependsOn(":sdks:python:test-suites:direct:py39:postCommitIT")
  dependsOn(":sdks:python:test-suites:direct:py39:hdfsIntegrationTest")
  dependsOn(":sdks:python:test-suites:portable:py39:postCommitPy39")
}

tasks.register("portablePythonPreCommit") {
  dependsOn(":sdks:python:test-suites:portable:py36:preCommitPy36")
  dependsOn(":sdks:python:test-suites:portable:py39:preCommitPy39")
}

tasks.register("pythonSparkPostCommit") {
  dependsOn(":sdks:python:test-suites:portable:py36:sparkValidatesRunner")
  dependsOn(":sdks:python:test-suites:portable:py37:sparkValidatesRunner")
  dependsOn(":sdks:python:test-suites:portable:py38:sparkValidatesRunner")
  dependsOn(":sdks:python:test-suites:portable:py39:sparkValidatesRunner")
}

tasks.register("websitePreCommit") {
  dependsOn(":website:preCommit")
}

tasks.register("communityMetricsPreCommit") {
  dependsOn(":beam-test-infra-metrics:preCommit")
}

tasks.register("communityMetricsProber") {
  dependsOn(":beam-test-infra-metrics:checkProber")
}

tasks.register("javaExamplesDataflowPrecommit") {
  dependsOn(":runners:google-cloud-dataflow-java:examples:preCommit")
  dependsOn(":runners:google-cloud-dataflow-java:examples-streaming:preCommit")
}

tasks.register("runBeamDependencyCheck") {
  dependsOn(":dependencyUpdates")
  dependsOn(":sdks:python:dependencyUpdates")
}

tasks.register("whitespacePreCommit") {
  // TODO(BEAM-9980): Find a better way to specify the tasks without hardcoding py version.
  dependsOn(":sdks:python:test-suites:tox:py38:archiveFilesToLint")
  dependsOn(":sdks:python:test-suites:tox:py38:unpackFilesToLint")
  dependsOn(":sdks:python:test-suites:tox:py38:whitespacelint")
}

tasks.register("typescriptPreCommit") {
  // TODO(BEAM-9980): Find a better way to specify the tasks without hardcoding py version.
  dependsOn(":sdks:python:test-suites:tox:py38:eslint")
  dependsOn(":sdks:python:test-suites:tox:py38:jest")
}

tasks.register("pushAllDockerImages") {
  dependsOn(":runners:spark:2:job-server:container:dockerPush")
  dependsOn(":runners:spark:3:job-server:container:dockerPush")
  dependsOn(":sdks:java:container:pushAll")
  dependsOn(":sdks:python:container:pushAll")
  dependsOn(":sdks:go:container:pushAll")
  for (version in project.ext.get("allFlinkVersions") as Array<*>) {
    dependsOn(":runners:flink:${version}:job-server-container:dockerPush")
  }
}

// Use this task to validate the environment set up for Go, Python and Java
tasks.register("checkSetup") {
  dependsOn(":sdks:go:examples:wordCount")
  dependsOn(":sdks:python:wordCount")
  dependsOn(":examples:java:wordCount")
}

// Configure the release plugin to do only local work; the release manager determines what, if
// anything, to push. On failure, the release manager can reset the branch without pushing.
release {
  revertOnFail = false
  tagTemplate = "v${version}"
  // workaround from https://github.com/researchgate/gradle-release/issues/281#issuecomment-466876492
  release {
    with (propertyMissing("git") as net.researchgate.release.GitAdapter.GitConfig) {
      requireBranch = "release-.*|master"
      pushToRemote = ""
    }
  }
}

// Reports linkage errors across multiple Apache Beam artifact ids.
//
// To use (from the root of project):
//    ./gradlew -Ppublishing -PjavaLinkageArtifactIds=artifactId1,artifactId2,... :checkJavaLinkage
//
// For example:
//    ./gradlew -Ppublishing -PjavaLinkageArtifactIds=beam-sdks-java-core,beam-sdks-java-io-jdbc :checkJavaLinkage
//
// Note that this task publishes artifacts into your local Maven repository.
if (project.hasProperty("javaLinkageArtifactIds")) {
  if (!project.hasProperty("publishing")) {
    throw GradleException("You can only check linkage of Java artifacts if you specify -Ppublishing on the command line as well.")
  }

  val linkageCheckerJava by configurations.creating
  dependencies {
    linkageCheckerJava("com.google.cloud.tools:dependencies:1.5.6")
  }

  // We need to evaluate all the projects first so that we can find depend on all the
  // publishMavenJavaPublicationToMavenLocal tasks below.
  for (p in rootProject.subprojects) {
    if (p.path != project.path) {
      evaluationDependsOn(p.path)
    }
  }

  project.tasks.register<JavaExec>("checkJavaLinkage") {
    dependsOn(project.getTasksByName("publishMavenJavaPublicationToMavenLocal", true /* recursively */))
    classpath = linkageCheckerJava
    mainClass.value("com.google.cloud.tools.opensource.classpath.LinkageCheckerMain")
    val javaLinkageArtifactIds: String = project.property("javaLinkageArtifactIds") as String? ?: ""
    var arguments = arrayOf("-a", javaLinkageArtifactIds.split(",").joinToString(",") {
      if (it.contains(":")) {
        "${project.ext.get("mavenGroupId")}:${it}"
      } else {
        // specify the version if not provided
        "${project.ext.get("mavenGroupId")}:${it}:${project.version}"
      }
    })

    // Exclusion file filters out existing linkage errors before a change
    if (project.hasProperty("javaLinkageWriteBaseline")) {
      arguments += "--output-exclusion-file"
      arguments += project.property("javaLinkageWriteBaseline") as String
    } else if (project.hasProperty("javaLinkageReadBaseline")) {
      arguments += "--exclusion-file"
      arguments += project.property("javaLinkageReadBaseline") as String
    }
    args(*arguments)
    doLast {
      println("NOTE: This task published artifacts into your local Maven repository. You may want to remove them manually.")
    }
  }
}
if (project.hasProperty("compileAndRunTestsWithJava11")) {
  tasks.getByName("javaPreCommitPortabilityApi").dependsOn(":sdks:java:testing:test-utils:verifyJavaVersion")
  tasks.getByName("javaExamplesDataflowPrecommit").dependsOn(":sdks:java:testing:test-utils:verifyJavaVersion")
  tasks.getByName("sqlPreCommit").dependsOn(":sdks:java:testing:test-utils:verifyJavaVersion")
} else if (project.hasProperty("compileAndRunTestsWithJava17")) {
  tasks.getByName("javaPreCommitPortabilityApi").dependsOn(":sdks:java:testing:test-utils:verifyJavaVersion17")
  tasks.getByName("javaExamplesDataflowPrecommit").dependsOn(":sdks:java:testing:test-utils:verifyJavaVersion17")
  tasks.getByName("sqlPreCommit").dependsOn(":sdks:java:testing:test-utils:verifyJavaVersion17")
} else {
  allprojects {
    tasks.withType(Test::class).configureEach {
      exclude("**/JvmVerification.class")
    }
  }
}
