/*
 * SPDX-License-Identifier: EUPL-1.2 OR LicenseRef-commercial
 *
 * Copyright (c) 2012-2026 mgm technology partners GmbH
 *
 * Dual License
 * ------------
 * This source file is part of the mgm A12 Platform and available under
 * a choice of two different licenses:
 *
 * 1. Open-Source License - EUPL v1.2
 *    You may redistribute and/or modify this file under the terms of the
 *    European Union Public License, version 1.2 - see https://eupl.eu/.
 *
 * 2. Commercial License
 *    Alternatively, you may obtain a commercial license from
 *    mgm technology partners GmbH, that permits use of this software
 *    under different terms (including support and maintenance services).
 *
 *    Please contact a12-license@mgm-tp.com for more information.
 *
 * You must select and comply with exactly one of the above license options.
 *
 * Warranty Disclaimer (applies to either option)
 * ----------------------------------------------
 * THIS SOFTWARE IS PROVIDED "AS IS" AND WITHOUT WARRANTY OF ANY KIND,
 * WHETHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT, EXCEPT WHERE SUCH DISCLAIMERS ARE HELD TO BE
 * LEGALLY INVALID. SEE THE RESPECTIVE LICENSE TEXT FOR DETAILS.
 */
package com.mgmtp.a12.devtools.gradle.plugins.integration

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class IntegrationFunctionalTest extends Specification {

    @TempDir
    File testProjectDir

    File settingsFile
    File buildFile

    def setup() {
        testProjectDir = testProjectDir.canonicalFile
        settingsFile = new File(testProjectDir, 'settings.gradle')
        settingsFile << "rootProject.name = 'test-project'\n"
        buildFile = new File(testProjectDir, 'build.gradle')
    }

    private GradleRunner runner(String... args) {
        return GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments(args)
            .forwardOutput()
    }

    def "registers the three integration tasks in the integration build group"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('tasks', '--group', 'integration build').build()

        then:
        result.output.contains('prepareForIntegration')
        result.output.contains('buildAndTestForIntegration')
        result.output.contains('publishForIntegration')
    }

    def "prepareForIntegration rewrites npm catalog + dataservices gradle toml key and prints the version quietly"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '17.1.0-build.20260710'
        """.stripIndent()

        // A realistic workspace: comments, unrelated catalogs, numeric/boolean config and
        // an `overrides` block with the kind of special keys pnpm uses. All of it must
        // survive the YAML round-trip intact - only the a12 catalog versions may change.
        new File(testProjectDir, 'pnpm-workspace.yaml') << '''\
catalog:
  typescript: ^6.0.3
catalogs:
  a12:
    # bumped by the integration build
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
    "@com.mgmtp.a12.dataservices/dataservices-access": 39.0.1
    "@com.mgmtp.a12.base/base-model-api": 30.0.1
  a12ranges:
    "@com.mgmtp.a12.widgets/widgets-core": ^39.0.0
minimumReleaseAge: 10080
overrides:
  "ajv@>=7.0.0-alpha.0": ">=8.18.0"
  "libxmljs2": "-"
gitChecks: false
'''.stripIndent()

        def gradleDir = new File(testProjectDir, 'gradle')
        gradleDir.mkdirs()
        new File(gradleDir, 'libs.versions.toml') << '''\
[versions]
dataservices = "39.0.1"
'''.stripIndent()

        when:
        def result = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=widgets=39.1.1-build.20260710,data-services=39.2.0-build.20260710',
            '-q'
        ).build()

        then: 'own version is the only quiet output'
        result.output.trim() == '17.1.0-build.20260710'

        and: 'npm catalog entries for the passed components rewritten, others left as-is'
        def ws = new groovy.yaml.YamlSlurper().parse(new File(testProjectDir, 'pnpm-workspace.yaml'))
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260710'
        ws.catalogs.a12.'@com.mgmtp.a12.dataservices/dataservices-access' == '39.2.0-build.20260710'
        ws.catalogs.a12ranges.'@com.mgmtp.a12.widgets/widgets-core' == '39.1.1-build.20260710'
        ws.catalogs.a12.'@com.mgmtp.a12.base/base-model-api' == '30.0.1'

        and: 'unrelated parts of the workspace survive the round-trip'
        ws.catalog.typescript == '^6.0.3'
        ws.minimumReleaseAge == 10080
        ws.gitChecks == false
        ws.overrides.'ajv@>=7.0.0-alpha.0' == '>=8.18.0'
        ws.overrides.libxmljs2 == '-'

        and: 'gradle toml key rewritten'
        new File(gradleDir, 'libs.versions.toml').text.contains('dataservices = "39.2.0-build.20260710"')
    }

    def "prepareForIntegration rewrites the prepare-models gradle toml key (no npm coordinates)"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '5.0.0-build.20260710'
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        def gradleDir = new File(testProjectDir, 'gradle')
        gradleDir.mkdirs()
        new File(gradleDir, 'libs.versions.toml') << '''\
[versions]
prepare-models = "1.0.0"
'''.stripIndent()

        when:
        def result = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=prepare-models=1.2.0-build.20260710',
            '-q'
        ).build()

        then: 'own version is the only quiet output'
        result.output.trim() == '5.0.0-build.20260710'

        and: 'gradle toml key rewritten'
        new File(gradleDir, 'libs.versions.toml').text.contains('prepare-models = "1.2.0-build.20260710"')
    }

    def "prepareForIntegration rewrites a custom libsVersionsFile configured via the extension"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '17.1.0-build.20260710'
            integration {
                libsVersionsFile = layout.projectDirectory.file('gradle/custom.versions.toml')
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        def gradleDir = new File(testProjectDir, 'gradle')
        gradleDir.mkdirs()
        // The default location is present but must be left untouched.
        new File(gradleDir, 'libs.versions.toml') << '''\
[versions]
dataservices = "39.0.1"
'''.stripIndent()
        new File(gradleDir, 'custom.versions.toml') << '''\
[versions]
dataservices = "39.0.1"
'''.stripIndent()

        when:
        def result = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=data-services=39.2.0-build.20260710',
            '-q'
        ).build()

        then: 'own version is the only quiet output'
        result.output.trim() == '17.1.0-build.20260710'

        and: 'the configured toml is rewritten'
        new File(gradleDir, 'custom.versions.toml').text.contains('dataservices = "39.2.0-build.20260710"')

        and: 'the default toml is left untouched'
        new File(gradleDir, 'libs.versions.toml').text.contains('dataservices = "39.0.1"')
    }

    def "prepareForIntegration with no dependency versions just prints the version"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '7.1.0-build.20260710'
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('prepareForIntegration', '-q').build()

        then:
        result.output.trim() == '7.1.0-build.20260710'
    }

    def "prepareForIntegration works in a project without a pnpm-workspace.yaml"() {
        given: 'a gradle-only repo: no pnpm-workspace.yaml, just a stray root package.json'
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '9.9.0-build.20260710'
        """.stripIndent()
        new File(testProjectDir, 'package.json') << '{"name": "test-project", "version": "9.9.0-SNAPSHOT"}\n'

        def gradleDir = new File(testProjectDir, 'gradle')
        gradleDir.mkdirs()
        new File(gradleDir, 'libs.versions.toml') << '''\
[versions]
dataservices = "39.0.1"
'''.stripIndent()

        when:
        def result = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=data-services=39.2.0-build.20260710',
            '-q'
        ).build()

        then: 'own version is the only quiet output'
        result.output.trim() == '9.9.0-build.20260710'

        and: 'gradle toml key rewritten'
        new File(gradleDir, 'libs.versions.toml').text.contains('dataservices = "39.2.0-build.20260710"')

        and: 'without a workspace file, version stamping is skipped entirely - package.json is untouched'
        new groovy.json.JsonSlurper().parse(new File(testProjectDir, 'package.json')).version == '9.9.0-SNAPSHOT'
    }

    def "prepareForIntegration works in a project without any npm files at all"() {
        given: 'neither pnpm-workspace.yaml nor package.json'
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '9.9.0-build.20260710'
        """.stripIndent()

        when:
        def result = runner('prepareForIntegration', '-q').build()

        then:
        result.output.trim() == '9.9.0-build.20260710'
    }

    def "prepareForIntegration rewrites additional components registered via extension"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '2.0.0-SNAPSHOT'
            integration {
                additionalComponents = [
                    'my-custom': [npm: ['@com.mgmtp.a12.custom/custom-lib'], toml: ['my-custom']],
                ]
            }
        """.stripIndent()

        new File(testProjectDir, 'pnpm-workspace.yaml') << '''\
catalogs:
  a12:
    "@com.mgmtp.a12.custom/custom-lib": 1.0.0
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent()

        def gradleDir = new File(testProjectDir, 'gradle')
        gradleDir.mkdirs()
        new File(gradleDir, 'libs.versions.toml') << '''\
[versions]
my-custom = "1.0.0"
'''.stripIndent()

        when:
        def result = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=my-custom=1.5.0-build.20260715,widgets=39.2.0-build.20260715',
            '-q'
        ).build()

        then: 'own version printed'
        result.output.trim() ==~ /2\.0\.0-build\.\d{14}\.integration/

        and: 'additional component npm coordinate rewritten'
        def ws = new groovy.yaml.YamlSlurper().parse(new File(testProjectDir, 'pnpm-workspace.yaml'))
        ws.catalogs.a12.'@com.mgmtp.a12.custom/custom-lib' == '1.5.0-build.20260715'

        and: 'built-in component still rewritten as well'
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.2.0-build.20260715'

        and: 'additional component gradle toml key rewritten'
        new File(gradleDir, 'libs.versions.toml').text.contains('my-custom = "1.5.0-build.20260715"')
    }

    def "prepareForIntegration rewrites an extra pnpm catalog registered via extension"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '17.1.0-build.20260710'
            integration {
                additionalPnpmCatalogs = ['myCatalog']
            }
        """.stripIndent()

        new File(testProjectDir, 'pnpm-workspace.yaml') << '''\
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
  myCatalog:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent()

        when:
        def result = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=widgets=39.2.0-build.20260715',
            '-q'
        ).build()

        then: 'own version is the only quiet output'
        result.output.trim() == '17.1.0-build.20260710'

        and: 'the built-in a12 catalog and the extra myCatalog are both rewritten'
        def ws = new groovy.yaml.YamlSlurper().parse(new File(testProjectDir, 'pnpm-workspace.yaml'))
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.2.0-build.20260715'
        ws.catalogs.myCatalog.'@com.mgmtp.a12.widgets/widgets-core' == '39.2.0-build.20260715'
    }

    def "prepareForIntegration is configuration-cache compatible across two runs"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '3.1.0-build.20260710'
        """.stripIndent()

        new File(testProjectDir, 'pnpm-workspace.yaml') << '''\
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent()

        def gradleDir = new File(testProjectDir, 'gradle')
        gradleDir.mkdirs()
        new File(gradleDir, 'libs.versions.toml') << '''\
[versions]
dataservices = "39.0.1"
'''.stripIndent()

        when: 'first run stores the configuration cache entry'
        def first = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=widgets=39.1.1-build.20260710',
            '--configuration-cache'
        ).build()

        then:
        !first.output.contains('problems were found storing the configuration cache')

        when: 'second run reuses the stored configuration cache entry'
        def second = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=widgets=39.1.2-build.20260710',
            '--configuration-cache'
        ).build()

        then:
        second.output.contains('Reusing configuration cache')
        !second.output.contains('problems were found storing the configuration cache')
    }

    def "buildAndTestForIntegration is configuration-cache compatible across two runs"() {
        given:
        buildFile << """
            plugins {
                id 'base'
                id 'com.mgmtp.a12.devtools.plugins.integration'
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when: 'first run stores the configuration cache entry'
        def first = runner('buildAndTestForIntegration', '--configuration-cache').build()

        then:
        !first.output.contains('problems were found storing the configuration cache')

        when: 'second run reuses the stored configuration cache entry'
        def second = runner('buildAndTestForIntegration', '--configuration-cache').build()

        then:
        second.output.contains('Reusing configuration cache')
        !second.output.contains('problems were found storing the configuration cache')
    }

    def "publishForIntegration is configuration-cache compatible across two runs"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            tasks.register('npmPublish')
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when: 'first run stores the configuration cache entry'
        def first = runner('publishForIntegration', '--configuration-cache').build()

        then:
        !first.output.contains('problems were found storing the configuration cache')

        when: 'second run reuses the stored configuration cache entry'
        def second = runner('publishForIntegration', '--configuration-cache').build()

        then:
        second.output.contains('Reusing configuration cache')
        !second.output.contains('problems were found storing the configuration cache')
    }

    def "prepareForIntegration rewrites npm catalog without gradle/libs.versions.toml"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '12.5.0-build.test'
        """.stripIndent()

        new File(testProjectDir, 'pnpm-workspace.yaml') << '''\
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent()

        when:
        def result = runner(
            'prepareForIntegration',
            '-Pa12DependencyVersions=widgets=39.9.9-build.test',
            '-q'
        ).build()

        then: 'own version is the only quiet output'
        result.output.trim() == '12.5.0-build.test'

        and: 'npm catalog entry rewritten'
        def ws = new groovy.yaml.YamlSlurper().parse(new File(testProjectDir, 'pnpm-workspace.yaml'))
        ws.catalogs.a12.'@com.mgmtp.a12.widgets/widgets-core' == '39.9.9-build.test'
    }

    def "prepareForIntegration stamps integration build-version into workspace package.json files"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            version = '39.1.0-SNAPSHOT'
        """.stripIndent()

        new File(testProjectDir, 'pnpm-workspace.yaml') << '''\
packages:
  - "core"
catalogs:
  a12:
    "@com.mgmtp.a12.widgets/widgets-core": 39.0.1
'''.stripIndent()

        // Root package.json
        new File(testProjectDir, 'package.json') << '{"name": "root", "version": "39.1.0-SNAPSHOT"}\n'
        // Workspace package
        def coreDir = new File(testProjectDir, 'core')
        coreDir.mkdirs()
        new File(coreDir, 'package.json') << '{"name": "@myorg/core", "version": "39.1.0-SNAPSHOT"}\n'

        when:
        def result = runner('prepareForIntegration', '-q').build()

        then: 'printed version is the integration build-version (a UTC yyyyMMddHHmmss timestamp)'
        result.output.trim() ==~ /39\.1\.0-build\.\d{14}\.integration/

        and: 'root package.json stamped'
        def rootPkg = new groovy.json.JsonSlurper().parse(new File(testProjectDir, 'package.json'))
        (rootPkg.version as String) ==~ /39\.1\.0-build\.\d{14}\.integration/

        and: 'workspace package.json stamped'
        def corePkg = new groovy.json.JsonSlurper().parse(new File(coreDir, 'package.json'))
        (corePkg.version as String) ==~ /39\.1\.0-build\.\d{14}\.integration/
    }

    def "buildAndTestForIntegration depends on 'build' by default"() {
        given:
        buildFile << """
            plugins {
                id 'base'
                id 'com.mgmtp.a12.devtools.plugins.integration'
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('buildAndTestForIntegration', '--dry-run').build()

        then:
        result.output.contains(':build ')
    }

    def "buildAndTestForIntegration builds every project by default, like the command line does"() {
        given: 'a multi-project build whose java tests live in a subproject'
        settingsFile << "include 'form-model'\n"
        buildFile << """
            plugins {
                id 'base'
                id 'com.mgmtp.a12.devtools.plugins.integration'
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        def subDir = new File(testProjectDir, 'form-model')
        subDir.mkdirs()
        new File(subDir, 'build.gradle') << "plugins { id 'java' }\n"

        when:
        def result = runner('buildAndTestForIntegration', '--dry-run').build()

        then: 'the root lifecycle task still runs'
        result.output.contains(':build ')

        and: 'so does the subproject build, and with it the subproject tests'
        result.output.contains(':form-model:build ')
        result.output.contains(':form-model:test ')
    }

    def "an explicitly configured buildTasks name is resolved against the root project only"() {
        given: 'a multi-project build that names the same task the default would use'
        settingsFile << "include 'form-model'\n"
        buildFile << """
            plugins {
                id 'base'
                id 'com.mgmtp.a12.devtools.plugins.integration'
            }
            integration {
                buildTasks = ['build']
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        def subDir = new File(testProjectDir, 'form-model')
        subDir.mkdirs()
        new File(subDir, 'build.gradle') << "plugins { id 'java' }\n"

        when:
        def result = runner('buildAndTestForIntegration', '--dry-run').build()

        then: 'the root task is wired'
        result.output.contains(':build ')

        and: 'a repo that pinned its own list keeps the surface it pinned'
        !result.output.contains(':form-model:build ')
    }

    def "buildAndTestForIntegration honours a custom buildTasks override"() {
        given:
        buildFile << """
            plugins {
                id 'base'
                id 'com.mgmtp.a12.devtools.plugins.integration'
            }
            tasks.register('integrationCheck')
            integration {
                buildTasks = ['integrationCheck']
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('buildAndTestForIntegration', '--dry-run').build()

        then:
        result.output.contains(':integrationCheck ')
        !result.output.contains(':build ')
    }

    def "publishForIntegration depends on npmPublish and maven publish, never docker"() {
        given:
        buildFile << """
            plugins {
                id 'maven-publish'
                id 'com.mgmtp.a12.devtools.plugins.integration'
            }
            group = 'com.mgmtp.a12.test'
            version = '1.0.0'
            // Stubs mirroring the real repos' task names.
            tasks.register('npmPublish')
            tasks.register('buildDockerImage')
            tasks.register('pushDockerImage')
            publishing {
                publications { mavenJava(MavenPublication) { from components.java } }
                repositories { maven { name = 'test'; url = layout.buildDirectory.dir('repo') } }
            }
        """.stripIndent()
        // maven-publish needs *some* component; add the java plugin via base isn't enough, so:
        buildFile.text = buildFile.text.replace(
            "id 'maven-publish'",
            "id 'java'\n                id 'maven-publish'")
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('publishForIntegration', '--dry-run').build()

        then:
        result.output.contains(':npmPublish ')
        result.output.contains(':publish ')

        and: 'docker image tasks are not pulled in'
        !result.output.contains(':buildDockerImage ')
        !result.output.contains(':pushDockerImage ')
    }

    def "publishForIntegration depends on runSecurityChecks when it exists"() {
        given:
        settingsFile << "include 'security'\n"
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            tasks.register('npmPublish')
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        def secDir = new File(testProjectDir, 'security')
        secDir.mkdirs()
        new File(secDir, 'build.gradle') << "tasks.register('runSecurityChecks')\n"

        when:
        def result = runner('publishForIntegration', '--dry-run').build()

        then:
        result.output.contains(':security:runSecurityChecks ')
        result.output.contains(':npmPublish ')
    }

    def "publishForIntegration runs the security checks before anything is published"() {
        given: 'a repo with both npm and maven publish channels alongside a security scan'
        settingsFile << "include 'core', 'security'\n"
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        def coreDir = new File(testProjectDir, 'core')
        coreDir.mkdirs()
        new File(coreDir, 'build.gradle') << """
            plugins { id 'maven-publish' }
            tasks.register('npmPublish')
        """.stripIndent()

        def secDir = new File(testProjectDir, 'security')
        secDir.mkdirs()
        new File(secDir, 'build.gradle') << "tasks.register('runSecurityChecks')\n"

        when:
        def result = runner('publishForIntegration', '--dry-run').build()

        then: 'a failing scan must abort the run before an artifact leaves the machine'
        result.output.indexOf(':security:runSecurityChecks ') < result.output.indexOf(':core:npmPublish ')
        result.output.indexOf(':security:runSecurityChecks ') < result.output.indexOf(':core:publish ')
    }

    def "publishForIntegration honours a custom publishTasks override instead of auto-detection"() {
        given: 'a repo that does not use the standard npm/maven publish tasks'
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            tasks.register('npmPublish')
            tasks.register('myCustomPublish')
            integration {
                publishTasks = ['myCustomPublish']
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('publishForIntegration', '--dry-run').build()

        then: 'only the configured task is wired'
        result.output.contains(':myCustomPublish ')

        and: 'the auto-detected npmPublish is not pulled in'
        !result.output.contains(':npmPublish ')
    }

    def "publishForIntegration publishes nothing when publishTasks is explicitly set to an empty list"() {
        given: 'a repo that opts out of publishing altogether'
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            tasks.register('npmPublish')
            integration {
                publishTasks = []
            }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('publishForIntegration', '--dry-run').build()

        then: 'the task itself still exists and runs'
        result.output.contains(':publishForIntegration ')

        and: 'auto-detection is switched off - nothing is published'
        !result.output.contains(':npmPublish ')
    }

    def "publishForIntegration depends only on npmPublish when there is no maven-publish"() {
        given:
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
            tasks.register('npmPublish')
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        when:
        def result = runner('publishForIntegration', '--dry-run').build()

        then:
        result.output.contains(':npmPublish ')
    }

    def "publishForIntegration excludes subprojects named docker, *devapp*, and documentation"() {
        given: 'a multi-project build with excluded subproject names'
        settingsFile << "include 'core', 'docker', 'devappServices', 'content-elements-devapp', 'documentation'\n"
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        ['core', 'docker', 'devappServices', 'content-elements-devapp', 'documentation'].each { name ->
            def dir = new File(testProjectDir, name)
            dir.mkdirs()
            new File(dir, 'build.gradle') << "tasks.register('npmPublish')\n"
        }

        when:
        def result = runner('publishForIntegration', '--dry-run').build()

        then: 'core npmPublish is included'
        result.output.contains(':core:npmPublish ')

        and: 'excluded subprojects are not wired'
        !result.output.contains(':docker:npmPublish ')
        !result.output.contains(':devappServices:npmPublish ')
        !result.output.contains(':content-elements-devapp:npmPublish ')
        !result.output.contains(':documentation:npmPublish ')
    }

    def "does not blow up configuration when a subproject task references a sibling registered later (docker-style wiring)"() {
        given: 'a multi-project consumer build, mimicking client/form-engine'
        settingsFile << "include 'sub'\n"
        buildFile << """
            plugins { id 'com.mgmtp.a12.devtools.plugins.integration' }
        """.stripIndent()
        new File(testProjectDir, 'pnpm-workspace.yaml') << "catalogs:\n  a12:\n"

        def subDir = new File(testProjectDir, 'sub')
        subDir.mkdirs()
        new File(subDir, 'build.gradle') << """
            // Mirrors the real docker task wiring: a task registered first configures
            // a dependency on a sibling task that is only registered afterwards.
            tasks.register('pushImage') { it.finalizedBy(tasks.named('deployCluster')) }
            tasks.register('deployCluster')
        """.stripIndent()

        when: 'just listing tasks must not crash project configuration'
        def result = runner('tasks').build()

        then:
        noExceptionThrown()
        result.output.contains('prepareForIntegration')
    }
}
