# ScalaCollider Site Generation

This directory contains a project which can generate
the [ScalaCollider GitHub page](http://sciss.github.io/ScalaCollider). This includes the API docs, which will be
published to [sciss.github.io/ScalaCollider/latest/api](http://sciss.github.io/ScalaCollider/latest/api). The docs
are based on [paradox](https://github.com/lightbend/paradox).

It can also be used to build a local copy of the docs. For the API, other than the main project doc task, this
uses [sbt-unidoc](https://github.com/sbt/sbt-unidoc) to produce combined API docs for ScalaOSC, ScalaAudioFile,
ScalaCollider-UGens and ScalaCollider.

In order for this to work, it pulls in these libraries directly by closing their respective GitHub repositories
and building them from source. For them to stay in sync, we use git version tags. For example in `build.sbt`, you
can see that the URL for ScalaOSC is `"git://github.com/Sciss/ScalaOSC.git#v1.1.5"`. Therefore, when building the
unified docs, that repository is internally cloned by sbt, using the particular tag `v1.1.5`.

__To build the docs locally, run `sbt unidoc`. You can view the results via `open target/scala-2.11/unidoc/index.html`.__

Alternatively, you can run the site via a local web server as `sbt previewSite` which is a functionality of
the [sbt-site](https://github.com/sbt/sbt-site) plugin. I publish the results to GitHub using
`sbt ghpages-push-site` which is provided by the [sbt-ghpages](https://github.com/sbt/sbt-ghpages) plugin.
