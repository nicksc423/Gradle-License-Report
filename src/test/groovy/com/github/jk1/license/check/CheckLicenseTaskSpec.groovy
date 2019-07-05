/*
 * Copyright 2018 Evgeny Naumenko <jk.vc@mail.ru>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jk1.license.check

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class CheckLicenseTaskSpec extends Specification {
    @Rule
    final TemporaryFolder testProjectDir = new TemporaryFolder()

    File buildFile
    File localBuildCacheDirectory
    File allowed

    BuildResult result(String[] arguments) {
        return GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments(arguments)
            .build()
    }

    BuildResult failResult(String[] arguments) {
        return GradleRunner.create()
            .withPluginClasspath()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments(arguments)
            .buildAndFail()
    }

    def setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        localBuildCacheDirectory = testProjectDir.newFolder('.local-cache')
        testProjectDir.newFile('settings.gradle') << """
        buildCache {
            local {
                directory '${localBuildCacheDirectory.toURI()}'
            }
        }
    """
        allowed = testProjectDir.newFile('allowed-licenses.json') << """
        {
            "allowedLicenses":[
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*", 
                },
                { 
                    "moduleLicense": "Apache Software License, 
                    Version 1.1", "moduleName": "org.jetbrains.*" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1" 
                },
                { 
                    "moduleLicense": "Apache License, Version 2.0" 
                },
                { 
                    "moduleLicense": "The 2-Clause BSD License" 
                },
                { 
                    "moduleLicense": "The 3-Clause BSD License" 
                },
                { 
                    "moduleLicense": "COMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL), Version 1.0" 
                },
                { 
                    "moduleLicense": "MIT License" 
                },
                { 
                    "moduleLicense": ".*", "moduleName": "org.jetbrains" 
                }
            ]
        }"""
    }

    def "it should pass when only containing included licenses"() {
        given:
        buildFile << """
            import com.github.jk1.license.filter.*
      
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.2.70'
                id 'com.github.jk1.dependency-license-report' version '1.2'
            }

            apply plugin: 'java'

            group 'greeting'
            version '0.0.1'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile group: 'org.slf4j', name: 'slf4j-api', version: '1.7.25'
                compile group: 'org.slf4j', name: 'slf4j-simple', version: '1.7.25'
                compile "org.jetbrains.kotlin:kotlin-reflect"
                compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
            }

            compileKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            compileTestKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            licenseReport {
                filters = new LicenseBundleNormalizer()
                allowedLicensesFile = new File("${allowed.path}")
            }
        """

        when:
        BuildResult buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.SUCCESS

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE

        when:
        buildResult = result("--build-cache", "clean", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.FROM_CACHE

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE
    }

    def "it should fail when some dependencies not allowed and pass when allowedLicensesFile add new allowedLicenses"() {
        given:

        allowed.text = """
        {
            "allowedLicenses":[
                { 
                    "moduleLicense": "The 2-Clause BSD License" 
                },
                { 
                    "moduleLicense": "The 3-Clause BSD License" 
                }
            ]
        }"""
        buildFile << """
            import com.github.jk1.license.filter.*
            
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.2.70'
                id 'com.github.jk1.dependency-license-report' version '1.2'
            }

            apply plugin: 'java'

            group 'greeting'
            version '0.0.1'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile group: 'org.slf4j', name: 'slf4j-api', version: '1.7.25'
                compile group: 'org.slf4j', name: 'slf4j-simple', version: '1.7.25'
                compile "org.jetbrains.kotlin:kotlin-reflect"
                compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
            }

            compileKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            compileTestKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            licenseReport {
                filters = new LicenseBundleNormalizer()
                allowedLicensesFile = new File("${allowed.path}")
            }
        """

        when:
        BuildResult buildResult = failResult("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.FAILED

        when:
        allowed.text = """
        {
            "allowedLicenses":[
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*", 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1" 
                },
                { 
                    "moduleLicense": "Apache License, Version 2.0" 
                },
                { 
                    "moduleLicense": "The 2-Clause BSD License" 
                },
                { 
                    "moduleLicense": "The 3-Clause BSD License" 
                },
                { 
                    "moduleLicense": "cOMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL), Version 1.0" 
                },
                { 
                    "moduleLicense": "MIT License" 
                },
                { 
                    "moduleLicense": ".*", "moduleName": "org.jetbrains.*" 
                }
            ]
        }"""
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.SUCCESS

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE

        when:
        buildResult = result("--build-cache", "clean", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.FROM_CACHE

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE
    }

    def "it should fail when not containing included checkFile and pass when renew allowedLicensesFile"() {
        given:
        buildFile << """

            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.2.70'
                id 'com.github.jk1.dependency-license-report' version '1.2'
            }


            group 'greeting'
            version '0.0.1'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile group: 'org.slf4j', name: 'slf4j-api', version: '1.7.25'
                compile group: 'org.slf4j', name: 'slf4j-simple', version: '1.7.25'
                compile "org.jetbrains.kotlin:kotlin-reflect"
                compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
            }
            apply plugin: 'java'
            compileKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            compileTestKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            licenseReport {
                allowedLicensesFile = new File("./config/allowed-licenses.json")
            }
        """

        allowed.text = """
        {
            "allowedLicenses":[
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*", 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1" 
                },
                { 
                    "moduleLicense": "Apache License, Version 2.0" 
                },
                { 
                    "moduleLicense": "The 2-Clause BSD License" 
                },
                { 
                    "moduleLicense": "The 3-Clause BSD License" 
                },
                { 
                    "moduleLicense": "cOMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL), Version 1.0" 
                },
                { 
                    "moduleLicense": "MIT License" 
                },
                { 
                    "moduleLicense": ".*", "moduleName": "org.jetbrains.*" 
                }
            ]
        }"""

        when:
        BuildResult buildResult = failResult("checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.FAILED

        when:
        buildFile.text = """
            
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.2.70'
                id 'com.github.jk1.dependency-license-report' version '1.2'
            }


            group 'greeting'
            version '0.0.1'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile group: 'org.slf4j', name: 'slf4j-api', version: '1.7.25'
                compile group: 'org.slf4j', name: 'slf4j-simple', version: '1.7.25'
                compile "org.jetbrains.kotlin:kotlin-reflect"
                compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
            }
            apply plugin: 'java'
            compileKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            compileTestKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            licenseReport {
                allowedLicensesFile = new File("${allowed.path}")
            }
        """
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.SUCCESS

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE

        when:
        buildResult = result("--build-cache", "clean", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.FROM_CACHE

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE
    }

    def "it should fail when no allowedLicensesFile specified and pass when adding allowedLicensesFile"() {
        given:
        buildFile << """
            
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.2.70'
                id 'com.github.jk1.dependency-license-report' version '1.2'
            }

            group 'greeting'
            version '0.0.1'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile group: 'org.slf4j', name: 'slf4j-api', version: '1.7.25'
                compile group: 'org.slf4j', name: 'slf4j-simple', version: '1.7.25'
                compile "org.jetbrains.kotlin:kotlin-reflect"
                compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
            }
            apply plugin: 'java'
            compileKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            compileTestKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
        """

        allowed.text = """
        {
            "allowedLicenses":[
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*", 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1", 
                    "moduleName": "org.jetbrains.*" 
                },
                { 
                    "moduleLicense": "Apache Software License, Version 1.1" 
                },
                { 
                    "moduleLicense": "Apache License, Version 2.0" 
                },
                { 
                    "moduleLicense": "The 2-Clause BSD License" 
                },
                { 
                    "moduleLicense": "The 3-Clause BSD License" 
                },
                { 
                    "moduleLicense": "cOMMON DEVELOPMENT AND DISTRIBUTION LICENSE (CDDL), Version 1.0" 
                },
                { 
                    "moduleLicense": "MIT License" 
                },
                { 
                    "moduleLicense": ".*", "moduleName": "org.jetbrains.*" 
                }
            ]
        }"""

        when:
        BuildResult buildResult = result("checkLicense")

        then:
        thrown Exception

        when:
        buildFile.text = """
            
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '1.2.70'
                id 'com.github.jk1.dependency-license-report' version '1.2'
            }

            group 'greeting'
            version '0.0.1'

            repositories {
                mavenCentral()
            }

            dependencies {
                compile group: 'org.slf4j', name: 'slf4j-api', version: '1.7.25'
                compile group: 'org.slf4j', name: 'slf4j-simple', version: '1.7.25'
                compile "org.jetbrains.kotlin:kotlin-reflect"
                compile "org.jetbrains.kotlin:kotlin-stdlib-jdk8"
            }
            apply plugin: 'java'
            compileKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            compileTestKotlin {
                kotlinOptions.jvmTarget = "1.8"
            }
            licenseReport {
                allowedLicensesFile = new File("${allowed.path}")
            }
        """
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.SUCCESS

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE

        when:
        buildResult = result("--build-cache", "clean", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.FROM_CACHE

        when:
        buildResult = result("--build-cache", "checkLicense")

        then:
        buildResult.task(":checkLicense").outcome == TaskOutcome.UP_TO_DATE
    }
}
