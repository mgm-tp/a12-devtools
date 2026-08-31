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
package com.mgmtp.a12.devtools.gradle.plugins.buildutils

import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Exercises {@link Helper#workspacePackageDirs} against a real {@code pnpm} so the parsing
 * in {@link HelperSpec}'s stubs cannot drift from pnpm's actual output. Skipped where pnpm
 * is not on PATH.
 */
@Requires({ pnpmOnPath() })
class HelperPnpmIntegrationSpec extends Specification {

    @TempDir
    File repo

    static boolean pnpmOnPath() {
        try {
            Process process = Helper.getUnifiedCommandLine(['pnpm', '--version']).toList().execute()
            process.waitFor()
            return process.exitValue() == 0
        } catch (Exception ignored) {
            return false
        }
    }

    def 'resolves glob packages via pnpm without an install'() {
        given: 'a glob workspace with three packages and non-package directories alongside'
        new File(repo, 'pnpm-workspace.yaml').text = 'packages:\n  - ./*\n'
        writePackageJson('.', 'root-pkg', '4.0.0-SNAPSHOT')
        writePackageJson('core', 'core-pkg', '4.0.0-SNAPSHOT')
        writePackageJson('showcase', 'showcase-pkg', '4.0.0-SNAPSHOT')
        new File(repo, 'docker').mkdirs()
        new File(repo, 'scripts').mkdirs()

        and:
        def execOps = ProjectBuilder.builder().withProjectDir(repo).build()
            .services.get(org.gradle.process.ExecOperations)

        when:
        List<File> dirs = Helper.workspacePackageDirs(execOps, repo)

        then: 'pnpm reports the packages, root included'
        dirs*.name as Set == ['core', 'showcase', repo.name] as Set

        and: 'directories without a package.json are not packages'
        !(dirs*.name.contains('docker'))
        !(dirs*.name.contains('scripts'))

        and: 'no install was required'
        !new File(repo, 'node_modules').exists()
    }

    def 'stamps every glob-matched package end to end'() {
        given:
        new File(repo, 'pnpm-workspace.yaml').text = 'packages:\n  - ./*\n'
        writePackageJson('.', 'root-pkg', '4.0.0-SNAPSHOT')
        writePackageJson('core', 'core-pkg', '4.0.0-SNAPSHOT')
        writePackageJson('showcase', 'showcase-pkg', '4.0.0-SNAPSHOT')

        and:
        def execOps = ProjectBuilder.builder().withProjectDir(repo).build()
            .services.get(org.gradle.process.ExecOperations)

        when:
        Helper.setVersionInWorkspace(execOps, new File(repo, 'pnpm-workspace.yaml'), '4.0.0-build.9.integration')

        then:
        versionOf('.') == '4.0.0-build.9.integration'
        versionOf('core') == '4.0.0-build.9.integration'
        versionOf('showcase') == '4.0.0-build.9.integration'
    }

    def 'stamps literal directory entries, as client declares them'() {
        given:
        new File(repo, 'pnpm-workspace.yaml').text = 'packages:\n  - "core"\n  - "data"\n'
        writePackageJson('.', 'root-pkg', '1.0.0-SNAPSHOT')
        writePackageJson('core', 'core-pkg', '1.0.0-SNAPSHOT')
        writePackageJson('data', 'data-pkg', '1.0.0-SNAPSHOT')

        and:
        def execOps = ProjectBuilder.builder().withProjectDir(repo).build()
            .services.get(org.gradle.process.ExecOperations)

        when:
        Helper.setVersionInWorkspace(execOps, new File(repo, 'pnpm-workspace.yaml'), '1.0.0-build.4.integration')

        then:
        versionOf('.') == '1.0.0-build.4.integration'
        versionOf('core') == '1.0.0-build.4.integration'
        versionOf('data') == '1.0.0-build.4.integration'
    }

    def 'fails loudly when a configured pnpmCommand cannot enumerate the workspace'() {
        given: 'a workspace and an explicitly configured pnpm that does not exist'
        new File(repo, 'pnpm-workspace.yaml').text = 'packages:\n  - ./*\n'
        writePackageJson('.', 'root-pkg', '5.0.0-SNAPSHOT')
        writePackageJson('core', 'core-pkg', '5.0.0-SNAPSHOT')

        and:
        def execOps = ProjectBuilder.builder().withProjectDir(repo).build()
            .services.get(org.gradle.process.ExecOperations)

        when:
        Helper.setVersionInWorkspace(
            execOps, new File(repo, 'pnpm-workspace.yaml'), '5.0.0-build.1.integration',
            new File(repo, 'no-such-pnpm').absolutePath)

        then: 'the build fails rather than silently stamping only the root'
        Exception e = thrown()
        e.message.contains('Failed to enumerate the pnpm workspace')

        and: 'nothing was stamped'
        versionOf('core') == '5.0.0-SNAPSHOT'
    }

    def 'degrades to the root when no pnpmCommand is configured and pnpm cannot be found'() {
        given: 'a single-package repo and a PATH lookup that will not resolve'
        new File(repo, 'pnpm-workspace.yaml').text = 'packages:\n  - ./*\n'
        writePackageJson('.', 'root-pkg', '6.0.0-SNAPSHOT')

        and:
        def execOps = Stub(org.gradle.process.ExecOperations)
        execOps.exec(_) >> { throw new RuntimeException('pnpm not found') }

        when:
        List<File> dirs = Helper.workspacePackageDirs(execOps, repo, null)

        then: 'no exception, and the root is still returned'
        noExceptionThrown()
        dirs == [repo]
    }

    private void writePackageJson(String dir, String name, String version) {
        File target = new File(repo, dir)
        target.mkdirs()
        new File(target, 'package.json').text = """{
  "name": "${name}",
  "version": "${version}"
}
"""
    }

    private String versionOf(String dir) {
        return new JsonSlurper().parse(new File(new File(repo, dir), 'package.json')).version
    }
}
