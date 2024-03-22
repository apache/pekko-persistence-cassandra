# Contributing to the Apache Pekko Persistence Cassandra Plugin

We follow the standard GitHub [fork & pull](https://help.github.com/articles/using-pull-requests/#fork--pull) approach to pull requests. Just fork the official repo, develop in a branch, and submit a PR!

You're always welcome to submit your PR straight away and start the discussion (without reading the rest of this wonderful doc, or the README.md). The goal of these notes is to make your experience contributing to Apache Pekko as smooth and pleasant as possible. We're happy to guide you through the process once you've submitted your PR.

## The Pekko Community

If you have questions about the contribution process or discuss specific issues, please interact with the community using the following resources.

- [GitHub discussions](https://github.com/apache/pekko-persistence-cassandra/discussions): for questions and general discussion.
- [Pekko dev mailing list](https://lists.apache.org/list.html?dev@pekko.apache.org): for Pekko development discussions.
- [GitHub issues](https://github.com/apache/pekko-persistence-cassandra/issues): for bug reports and feature requests. Please search the existing issues before creating new ones. If you are unsure whether you have found a bug, consider asking in GitHub discussions or the mailing list first.

## Navigating around the project & codebase

### Branches summary

Depending on which version (or sometimes module) you want to work on, you should target a specific branch as explained below:

* `main` – active development branch of Pekko Persistence Cassandra
* `release-x.y` – maintenance branch of Pekko Persistence Cassandra x.y.z

## Pekko contributing guidelines

These guidelines apply to all Pekko projects, by which we currently mean both the `apache/pekko` repository, as well as any plugins or additional repositories.

These guidelines are meant to be a living document that should be changed and adapted as needed.
We encourage changes that make it easier to achieve our goals efficiently.

## General Workflow

The steps below describe how to get a patch into the main development branch (`main`).
The steps are exactly the same for everyone involved in the project, including the core team and first-time contributors.

1. To avoid duplicated effort, it might be good to check the [issue tracker](https://github.com/apache/pekko-persistence-cassandra/issues) and [existing pull requests](https://github.com/apache/pekko-persistence-cassandra/pulls) for existing work.
   - If there is no ticket yet, feel free to [create one](https://github.com/apache/pekko-persistence-cassandra/issues/new) to discuss the problem and the approach you want to take to solve it.
1. [Fork the project](https://github.com/apache/pekko-persistence-cassandra#fork-destination-box) on GitHub. You'll need to create a feature-branch for your work on your fork, as this way you'll be able to submit a pull request against the mainline Pekko.
1. Create a branch on your fork and work on the feature. For example: `git checkout -b custom-headers-pekko-http`
   - Please make sure to follow the general quality guidelines (specified below) when developing your patch.
   - Please write additional tests covering your feature and adjust existing ones if needed before submitting your pull request.
   - Use the `verifyCodeStyle` sbt task to ensure your code is properly formatted and includes the proper copyright headers.
1. Once your feature is complete, prepare the commit following our [Creating Commits And Writing Commit Messages](#creating-commits-and-writing-commit-messages). For example, a good commit message would be: `Adding compression support for Manifests #22222` (note the reference to the ticket it aimed to resolve).
1. If it's a new feature or a change of behavior, document it on the [docs](https://github.com/apache/pekko-persistence-cassandra/tree/main/docs). When the feature touches Scala and Java DSL, document both the Scala and Java APIs.
1. Now it's finally time to [submit the pull request](https://help.github.com/articles/using-pull-requests)!
   - Please make sure to include a reference to the issue you're solving *in the comment* for the Pull Request, as this will cause the PR to be linked properly with the issue. Examples of good phrases for this are: "Resolves #1234" or "Refs #1234".
1. If you are a first time contributor, a core member must approve the CI to run for your pull request.
1. For non-trivial changes, you will be asked to sign the [CLA](https://www.apache.org/licenses/contributor-agreements.html) if you have not done so before.
1. Now, both committers and interested people will review your code. This process ensures that the code we merge is of the best possible quality and that no silly mistakes slip through. You're expected to follow-up on these comments by adding new commits to the same branch. The commit messages of those commits can be more loose, for example: `Removed debugging using printline`, as they all will be squashed into one commit before merging into the main branch.
   - The community and core team are really nice people, so don't be afraid to ask follow-up questions if you didn't understand some comment or would like clarification on how to continue with a given feature. We're here to help, so feel free to ask and discuss any questions you might have during the review process!
1. After the review, you should fix the issues as needed (pushing a new commit for a new review, etc.), iterating until the reviewers give their approval signaled by GitHub's pull-request approval feature. Usually, a reviewer will add an `LGTM` comment, which means "Looks Good To Me".
   - In general, a PR is expected to get 2 approvals from the team before it is merged. If the PR is trivial or under exceptional circumstances (such as most of the core team being on vacation, a PR was very thoroughly reviewed/tested and surely is correct), a single LGTM may be fine as well.
1. If the code change needs to be applied to other branches as well (for example, a bugfix needing to be backported to a previous version), one of the team members will either ask you to submit a PR with the same commits to the old branch or will do this for you.
   - Follow the [backporting steps](#backporting) below.
1. Once everything is said and done, your pull request gets merged :tada: Your feature will be available with the next "earliest" release milestone (i.e. if backported so that it will be in release x.y.z, find the relevant milestone for that release). Of course, you will be given credit for the fix in the release stats during the release's announcement. You've made it!

The TL;DR; of the above very precise workflow version is:

1. Fork Pekko
2. Hack and test on your feature (on a branch)
3. Document it
4. Submit a PR
5. Sign the [CLA](https://www.apache.org/licenses/contributor-agreements.html) if necessary
6. Keep polishing it until getting the required number of approvals
7. Profit!

> **Note:** Github Actions runs all the workflows for the forked project. We have filters to ensure that each action effectively runs only for the `apache/pekko-persistence-cassandra` repository, but you may also want to [disable Github Actions](https://docs.github.com/en/github/administering-a-repository/managing-repository-settings/disabling-or-limiting-github-actions-for-a-repository) entirely in your fork.

#### Backporting

Backport pull requests such as these are marked using the phrase `for validation` in the title to make the purpose clear in the pull request list.
They can be merged once validation passes without additional review (if there are no conflicts).
Using, for example: current.version 2.5.22, previous.version 2.5, milestone.version 2.6.0-M1

1. Label this PR with `to-be-backported`
1. Mark this PR with Milestone `${milestone.version}`
1. Mark the issue with Milestone `${current.version}`
1. `git checkout release-${previous.version}`
1. `git pull`
1. Create wip branch
1. `git cherry-pick <commit>`
1. Open PR, target `release-${previous.version}`
1. Label that PR with `backport`
1. Merge backport PR after validation (no need for full PR reviews)
1. Close issue.

## Running the tests

The tests rely on a Cassandra instance running locally on port 9042. A docker-compose file is
provided in the root of the project to start this with `docker-compose up -d cassandra`

### Pull request requirements

For a pull request to be considered at all, it has to meet these requirements:

1. Regardless if the code introduces new features or fixes bugs or regressions, it must have comprehensive tests.
1. The code must be well documented as per the existing documentation format (see the 'Documentation' section below).
1. The commit messages must properly describe the changes. See further below.
1. A pull request must be [linked to the issue](https://docs.github.com/en/issues/tracking-your-work-with-issues/linking-a-pull-request-to-an-issue) it aims to resolve in the PR's description (or comments). This can be achieved by writing "Fixes #1234" or similar in PR description.
1. Licensing rules:
   - Existing files with copyright statements must leave those copyright statements intact
   - New files should have an Apache license header instead. For an example of this, see [this file](https://github.com/apache/poi/blob/trunk/poi/src/main/java/org/apache/poi/POIDocument.java).

## Documentation

pekko-persistence-cassandra is currently documented in the README.md. If we
were to provide more extensive documentation, [paradox](https://github.com/apache/pekko-sbt-paradox) would be the tool of choice.
See the [Apache Pekko Connectors](https://github.com/apache/pekko-connectors) project for an example.

## External Dependencies

All the external runtime dependencies for the project, including transitive dependencies, must have an open source license that is equal to, or compatible with, [Apache 2](https://www.apache.org/licenses/LICENSE-2.0).

This must be ensured by manually verifying the license for all the dependencies for the project:

1. Whenever a committer to the project changes a version of a dependency (including Scala) in the build file.
2. Whenever a committer to the project adds a new dependency.
3. Whenever a new release is cut (public or private for a customer).

Which licenses are compatible with Apache 2 are defined in [this doc](https://www.apache.org/legal/resolved.html), where you can see that the licenses that are listed under ``Category A`` automatically compatible with Apache 2, while the ones listed under ``Category B`` needs additional action:

> Each license in this category requires some degree of reciprocity. This may mean that additional action is warranted in order to minimize the chance that a user of an Apache product will create a derivative work of a differently-licensed portion of an Apache product without being aware of the applicable requirements.

Each project must also create and maintain a list of all dependencies and their licenses, including all their transitive dependencies. This can be done in either in the documentation or in the build file next to each dependency.

## Work In Progress

It is ok to work on a public feature branch in the GitHub repository. Something that can sometimes be useful for early feedback etc. If so then it is preferable to name the branch accordingly. This can be done by either prefix the name with ``wip-`` as in ‘Work In Progress’, or use hierarchical names like ``wip/..``, ``feature/..`` or ``topic/..``. Either way is fine as long as it is clear that it is work in progress and not ready for merge. This work can temporarily have a lower standard. However, to be merged into main branch it will have to go through the regular process outlined above, with Pull Request, review etc..

Also, to facilitate both well-formed commits and working together, the ``wip`` and ``feature``/``topic`` identifiers also have special meaning.   Any branch labelled with ``wip`` is considered “git-unstable” and may be rebased and have its history rewritten.   Any branch with ``feature``/``topic`` in the name is considered “stable” enough for others to depend on when a group is working on a feature.

## Creating Commits And Writing Commit Messages

Follow these guidelines when creating public commits and writing commit messages.

1. If your work spans multiple local commits (for example; if you do safe point commits while working in a feature branch or work in a branch for long time doing merges/rebases etc.) then please do not commit it all but rewrite the history by squashing the commits into a single big commit which you write a good commit message for (like discussed in the following sections). For more info read this article: [Git Workflow](https://sandofsky.com/blog/git-workflow.html). Every commit should be able to be used in isolation, cherry picked etc.

2. First line should be a descriptive sentence what the commit is doing, including the ticket number. It should be possible to fully understand what the commit does—but not necessarily how it does it—by just reading this single line. We follow the “imperative present tense” style for commit messages ([more info here](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html)).

   It is **not ok** to only list the ticket number, type "minor fix" or similar.
   If the commit is a small fix, then you are done. If not, go to 3.

3. Following the single line description should be a blank line followed by an enumerated list with the details of the commit.

4. Add keywords for your commit (depending on the degree of automation we reach, the list may change over time):
    * ``Review by @gituser`` - if you want to notify someone on the team. The others can, and are encouraged to participate.

Example:

    Add eventsByTag query #123

    * Details 1
    * Details 2
    * Details 3

## Applying code style to the project

The project uses [scalafmt](https://scalameta.org/scalafmt/) to ensure code quality which is automatically checked on
every PR. If you would like to check for any potential code style problems locally you can run `sbt checkCodeStyle`
and if you want to apply the code style then you can run `sbt applyCodeStyle`.

## Ignoring formatting commits in git blame

Throughout the history of the codebase various formatting commits have been applied as the scalafmt style has evolved over time, if desired
one can setup git blame to ignore these commits. The hashes for these specific are stored in [this file](.git-blame-ignore-revs) so to configure
git blame to ignore these commits you can execute the following.

```shell
git config blame.ignoreRevsFile .git-blame-ignore-revs
```
