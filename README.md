# exlore-http-kit

Explore callbacks and threads in the `http-kit` library for Clojure.

See: http://www.http-kit.org/

## Installation

Download from https://github.com/madkins23/explore-http-kit/tree/master

## Usage

You could build a standalone jar but I usually:

    lein run

or

    lein run all

## Options

The sub-tests can be run with the following names:

* `simple`
* `nested`
* `chain`
* `multi`
* `pool`

For example:

    lein run simple chain

## Description

This Clojure program explores the way `http-kit` callbacks and threads interact.
See the `intro.md` file for more detail.

## License

Copyright © 2015 Marc M. Adkins

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

