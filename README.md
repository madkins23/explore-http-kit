# exlore-http-kit

Explore callbacks and threads in the `http-kit` library for Clojure.

See: http://www.http-kit.org/

## Installation

Download from https://github.com/madkins23/explore-http-kit/tree/master

## Usage

### Standalone

Build standalone jar using:

    lein uberjar

and run as:

    java -jar target/uberjar/explore-http-kit-0.1.0-SNAPSHOT-standalone.jar

### Leiningen

Run using Leiningen:

    lein run

or

    lein run all

## Options

The sub-tests can be run with any set of the following parameters:

* `simple`
* `nested`
* `chain`
* `multi`
* `pool`

For example:

    lein run simple chain

sWithout any options everything but the `multi` test will run.
Run _all_ tests using the `all` parameter.

## Description

This Clojure program explores the way `http-kit` callbacks and threads interact.
See the `intro.md` file for more detail.

## License

Copyright © 2015 Marc M. Adkins

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

