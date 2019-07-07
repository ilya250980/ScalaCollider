# ScalaCollider Site Generation

This directory contains a project which can generate
the [ScalaCollider home page](https://www.sciss.de/scalaCollider). This includes the API docs, which will be
published to [www.sciss.de/scalaCollider/latest/api](https://www.sciss.de/scalaCollider/latest/api). The docs
are based on [paradox](https://github.com/lightbend/paradox).

It can also be used to build a local copy of the docs. For the API, other than the main project doc task, this
uses [sbt-unidoc](https://github.com/sbt/sbt-unidoc) to produce combined API docs for ScalaOSC, ScalaAudioFile,
ScalaCollider-UGens and ScalaCollider.

In order for this to work, it pulls in these libraries directly by closing their respective GitHub repositories
and building them from source. For them to stay in sync, we use git version tags. For example in `build.sbt`, you
can see that the URL for ScalaOSC is like `"git://github.com/Sciss/ScalaOSC.git#v1.1.5"`. Therefore, when building the
unified docs, that repository is internally cloned by sbt, using a particular tag such as `v1.1.5`.

__Note:__ You have run sbt using `sbt ++2.12.8`, because some projects ignore `scalaVersion in ThisBuild` apparently.

## To build the docs locally

Run `sbt ++2.12.8 scalacollider-site/unidoc`. You can view the results via `open target/scala-2.12/unidoc/index.html`.

## To build a site preview

You can run the site via a local web server as `sbt ++2.12.8 scalacollider-site/previewSite` which is a functionality of
the [sbt-site](https://github.com/sbt/sbt-site) plugin. I publish the results to GitHub using
`sbt ++2.12.8 scalacollider-site/ghpagesPushSite` which is provided by the [sbt-ghpages](https://github.com/sbt/sbt-ghpages) plugin.

## sciss.de

To publish here, prepare the files using `sbt ++2.12.8 clean packageSite`. The directory `target/site/` contains the stuff
that must be uploaded, i.e. `scp -r target/site/* <credentials>@ssh.strato.de:scalaCollider/`

__TO-DO:__ `rsync` is a smarter option. Use it like the following:

    rsync -rltDvc --delete-after --exclude=.git target/site/ www.sciss.de@ssh.strato.de:scalaCollider/

(add `--dry-run` to check first).

It seems that `packageSite` does not pick up changes, e.g. to `index.md`; better run `previewSite` once.

## Publishing unified docs

To publish a unidoc only artifact:

    sbt ++2.12.8 scalacollider-unidoc/publishSigned

