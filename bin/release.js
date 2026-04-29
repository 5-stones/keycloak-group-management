#!/usr/bin/env node
// Sync the version from package.json into gradle.properties so the Gradle
// build produces a JAR with the matching version tag.
//
// Run automatically by the `version:gradle` npm script (which is invoked by
// the `version` lifecycle when `npm version <patch|minor|major>` runs).

const fs = require('fs')
const path = require('path')
const { version } = require('../package.json')

const propsPath = path.join(__dirname, '../gradle.properties')
const data = fs.readFileSync(propsPath).toString()

const reg = /^(artifactVersion=)(.*)$/m
if (!reg.test(data)) {
  console.error('Could not find `artifactVersion=...` line in gradle.properties')
  process.exit(1)
}

const updated = data.replace(reg, `$1${version}`)
fs.writeFileSync(propsPath, updated)
console.log(`Synced gradle.properties artifactVersion -> ${version}`)
