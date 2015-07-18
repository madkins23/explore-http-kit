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

Notice the two calls to the `simple` callback on the same thread,
the first has no retry flag and the second has the flag set to `true`.
This test should always succed with `status 200`.

### `nested`

The `nested` test executes the same HTTP call asynchronously twice as well.
Before the second call a "secret" is acquired _synchronously_ from a separate web service.<sup>2</sup>
This may be thought of as an access key, where the first call fails (due to
no or an old access key) and the secret must be acquired to authorize the retry.

In this case, on my machine<sup>1</sup>, the nested call for the secret waits forever.
The synchronous call never completes because it is executing in _the_ handler thread
(hold your obvious question, I'm getting there) so the `deref` is blocked.

In the test code I wrote here there is a timeout but that doesn't solve the problem.
The reason I added the timeout was to allow the tests to complete.
In the "real" code wherein I first noticed this behavior all processing halted.

Output for this test should like:

    ------------- Nested Retry ------------------
    Nested callback thread    10
    Nested          retry   null
    Nested          secret  null
    Nested          secret <timeout>
    Nested          status   401

First the secret acquisition times out and then, due to the way I wrote the code,
the timeout is noticed and a 401 status is returned via `deliver`.

### `chain`

The `chain` test provides a work-around for the thread blocking problem.
Instead of acquiring the secret synchonously the `chain` callback launches
an asynchronous request for the secret which is handled by a second callback.
The original call options and the `chain` callback function are passed along
so that the secret callback can invoke the chain callback a second time.

Output for this test should like:

    ------------- Chain Retry -------------------
    Chain callback  thread    10
    Chain           flag    null
    Chain           secret  null
    Secret callback thread    10
    Secret          secret rYv0G
    Chain callback  thread    10
    Chain           flag    true
    Chain           secret rYv0G
    Chain           status   200

Notice that the callback handlers are all operating on the same handler thread.
This solution is reminiscent of the way `node.js` works around the same constraint.

### `multi`

The `multi` test runs multiple (currently 5) `chain` tests in parallel.
The output should look like:

    ------------- Multi Retry -------------------
    Chain callback  thread    10
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    10
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    10
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    10
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    10
    Chain           flag    null
    Chain           secret  null
    Secret callback thread    10
    Secret          secret 3wnhI
    Secret callback thread    10
    Secret          secret GgYLF
    Secret callback thread    10
    Secret          secret 8PDwd
    Secret callback thread    10
    Secret          secret 2maUe
    Secret callback thread    10
    Secret          secret hvzfE
    Chain callback  thread    10
    Chain           flag    true
    Chain           secret 8PDwd
    Chain callback  thread    10
    Chain           flag    true
    Chain           secret GgYLF
    Multi           status   200
    Chain callback  thread    10
    Chain           flag    true
    Chain           secret 3wnhI
    Multi           status   200
    Chain callback  thread    10
    Chain           flag    true
    Chain           secret 2maUe
    Chain callback  thread    10
    Chain           flag    true
    Chain           secret hvzfE
    Multi           status   200
    Multi           status   200
    Multi           status   200

This test drives home the point that there is only one thread handling all callbacks.
Each callback is handled in sequence on thread 10.

Note that the `multi` test does not run unless you specify `multi` or `all` on
the command line.

## Threads and Pools

Why is there only one thread? The `http-kit` documentation suggests that there is a thread _pool_.
[Source code for the `http-kit` client](https://github.com/http-kit/http-kit/blob/master/src/org/httpkit/client.clj)
shows a default [Java `java.util.concurrent.ThreadPoolExecutor`](https://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ThreadPoolExecutor.html#ThreadPoolExecutor(int,%20int,%20long,%20java.util.concurrent.TimeUnit,%20java.util.concurrent.BlockingQueue,%20java.util.concurrent.ThreadFactory)).
Granted, the `ThreadPoolExecutor` is initialized with a maximum thread count the
same as the number of cores on one's machine, but my machine has more than one core.<sup>1</sup>

The output from `expore-http-kit.explore-thread-use` includes information about
the `org.httpkit.client/default-pool` object. It starts as:

    ------------- Thread Pool -------------------
    Default pool    core       0
    Default pool    size       0
    Default pool    high       0
    Default pool    max        4

and finishes (after the `multi` test) as:

    ------------- Thread Pool -------------------
    Default pool    core       0
    Default pool    size       1
    Default pool    high       1
    Default pool    max        4

I have no idea why the other threads are not used.

### `pool`

The `pool` test uses a different thread pool to execute the `nested` test.
This thread pool is created at the top of the main program _using the same code
that is used in `org.httpkit.client`_ but the result is different.
First, the initial configuration includes a non-zero number of core threads:

    ------------- Thread Pool -------------------
    My pool         core       4
    My pool         size       0
    My pool         high       0
    My pool         max        4

Then, the results of running the `nested` test (which previously hung or timed out)
with this pool are different:

    ------------- Nested Retry Pool -------------
    Nested callback thread    11
    Nested          retry   null
    Nested          secret  null
    Nested          secret xi0pm
    Nested callback thread    12
    Nested          retry   true
    Nested          secret xi0pm
    Nested          status   200

Note that _two threads_ are used here. The special pool works as expected.
After the test this thread pool has obviously been used:

    ------------- Thread Pool -------------------
    My pool         core       4
    My pool         size       2
    My pool         high       2
    My pool         max        4

I have no idea why the behavior of these two thread pools is so different.

### `multi pool`

If both `multi` and `pool` (or just `all`) are specified a `multi` test with
the special thread pool is executed. The results are as follows:

    ------------- Multi Retry Pool --------------
    Chain callback  thread    13
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    14
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    11
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    12
    Chain           flag    null
    Chain           secret  null
    Chain callback  thread    13
    Chain           flag    null
    Chain           secret  null
    Secret callback thread    14
    Secret          secret ERFaX
    Chain callback  thread    12
    Chain           flag    true
    Chain           secret ERFaX
    Secret callback thread    11
    Secret          secret yuiDU
    Secret callback thread    13
    Secret          secret a0iYq
    Secret callback thread    14
    Secret          secret iMq4k
    Secret callback thread    12
    Secret          secret VHbOj
    Chain callback  thread    11
    Chain           flag    true
    Chain           secret yuiDU
    Multi           status   200
    Chain callback  thread    13
    Chain           flag    true
    Chain           secret a0iYq
    Chain callback  thread    14
    Chain           flag    true
    Chain           secret iMq4k
    Multi           status   200
    Chain callback  thread    12
    Chain           flag    true
    Chain           secret VHbOj
    Multi           status   200
    Multi           status   200
    Multi           status   200

Once again we see different threads handling different callbacks.
After this test the special thread pool is now full up:

    ------------- Thread Pool -------------------
    My pool         core       4
    My pool         size       4
    My pool         high       4
    My pool         max        4

## FYI

As a side note, I would like to point out that Clojure in `http-kit` is a thin
layer over a lot of Java. Just compare these two links:<sup>3</sup>

* [Clojure source directory](https://github.com/http-kit/http-kit/tree/master/src/org/httpkit)
* [Java source directory](https://github.com/http-kit/http-kit/tree/master/src/java/org/httpkit)

That's sure a lot of object-oriented code hidden away behind a friendly Clojure facade.
I'm wondering if the problem is in Clojure, Java, or a chance combination of both.
Having spent a non-trivial amount of time reading a lot of this code I found nothing
that looked wrong and obviously, given a different thread pool, nothing _is_ wrong.

## Conclusions

There is some sort of odd behavior in `http-kit` or some code library it is using.
I can demonstrate it now in my current home and work environment.
I fully expect this behavior to go away with some change in Clojure and/or Java version.
I have no reason to believe that what I'm seeing is an `http-kit` problem.

In order to work around this I can suggest two options:

* chained callbacks so that multiple threads are unecessary and/or
* using a custom thread pool so that thread behavior is as expected.

Frankly, I would suggest _both_:

* No matter how many threads you configure in your pool they may all need _just one more thread_ at some point.
* You definitely want more than one thread whether you are chaining or not.

## Notes

<sup>1</sup> Ubuntu 14.04 running on a Lenovo T430 (4 cores) or HP (2 cores) equipment.

<sup>2</sup> https://www.random.org provides a random string.

<sup>3</sup> Great googly moogly!

