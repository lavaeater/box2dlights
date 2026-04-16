# Box2DLights - Pseudo 3d [development]

[![GitHub Actions Build Status](https://img.shields.io/github/actions/workflow/status/libgdx/box2dlights/main.yml?branch=master&label=GitHub%20Actions)](https://github.com/libgdx/box2dlights/actions?query=workflow%3A%22Build+and+deploy%22)

[![Latest Version](https://img.shields.io/nexus/r/com.badlogicgames.box2dlights/box2dlights?nexusVersion=2&server=https%3A%2F%2Foss.sonatype.org&label=Version)](https://search.maven.org/artifact/com.badlogicgames.box2dlights/box2dlights)
[![Snapshots](https://img.shields.io/nexus/s/com.badlogicgames.box2dlights/box2dlights?server=https%3A%2F%2Foss.sonatype.org&label=Snapshots)](https://oss.sonatype.org/#nexus-search;gav~com.badlogicgames.box2dlights~box2dlights~~~~kw,versionexpand)

[![screenshot](http://img.youtube.com/vi/8Jc5Xyy4yJU/0.jpg)](http://youtu.be/8Jc5Xyy4yJU)

This is the pseudo-3d implementation based on Kalle Hameleinen's Box2DLights.

It provides simple dynamic limited length fixtures-shaped shadows for 2d games with the "from top" camera view, where you can control the third pseudo dimension - "height" for both physics fixtures and lights.

This is in a quite an early development stage, so not everything is supported, and not everything works without bugs.

## Currently supported lights and fixture shapes:
 * Point Light
 * Directional Light (in progress)

## Fixture shapes:
   * PolygonShape
   * CircleShape
   * ChainShape (in progress)
   * EdgeShape (in progress)

This library offer easy way to add soft dynamic 2d lights to your physic based game. Rendering use libgdx, but it would be easy to port this to other frameworks or pure openGl too.

## Usage
 * Download the [latest Box2DLights release](http://libgdx.badlogicgames.com/box2dlights/)
 * Add the box2dlights.jar file to your libgdx core project's classpath
 * Check out the [Wiki](https://github.com/libgdx/box2dlights/wiki)

Box2DLights is also available in Maven Central. Add the following dependency to your libgdx core project:

    <dependency>
      <groupId>com.badlogicgames.box2dlights</groupId>
      <artifactId>box2dlights</artifactId>
      <version>1.5</version>
    </dependency>
    
If you use Gradle, add the following dependency to your build.gradle file, in the dependencies block of the core project:

     implementation "com.badlogicgames.box2dlights:box2dlights:1.5"

## Maintenance Note
Box2dlights was moved from Google Code to GitHub to make contributing easier. The libgdx team will happily merge pull requests but will not fix bugs or ensure compatibility with the latest libgdx version.
