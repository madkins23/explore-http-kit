# Introduction to explore-http-kit

See: http://www.http-kit.org/

After beating my head against a wall for some days in an enterprise-level
system using `http-kit` I finally wrote this code to figure out how it worked
(and why it sometimes did not). This is a fairly narrow exploration,
concentrating on callback handers and why the sometimes block.

There is only a single program in `expore-http-kit.explore-thread-use`.
It contains a series of "tests" that demonstrate some quirks and solutions.

## Individual Tests

The main routine runs through various "tests".
These are not unit tests, `expore-http-kit.explore-thread-use`
is intended to explore behavior in and of itself.

The following sections discuss the various tests:

### `simple`

The `simple` test executes the same HTTP call asynchronously twice.
The callback for the first call launches the same call with the same callback
and an extra `:retry` flag so that the second iteration will complete.
This might be thought of as the flow when handling an HTTP page that redirects.

The completion is signalled by using `deliver` to set the status on a `promise`.
The main routine waits on dereferencing the `promise`.

This case completes releases the callback handler thread immediately.
The second call is then handled by the same thread.

Output for this test should like:

  ------------- Simple Retry ------------------
  Simple callback thread    10
  Simple          flag    null
  Simple callback thread    10
  Simple          flag    true
  Simple          status   200

Notice the two calls to the `simple` callback,
the first has no retry flag and the second has the flag set to `true`.
This test should always succed with `status 200`.

### `nested`

### `chain`

### `multi`

## Threads and Pools

### `pool` and `multi pool`

