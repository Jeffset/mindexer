# mindexer - My (Maven) Indexer

A toy Maven indexer solution tailored specifically for Maven Central and KMP.
Does not use either Lucene/ready Maven indexes or https://search.maven.org, thus has
https://repo1.maven.org/maven2 as the sole info source.

Functionality:
- Building an index for each specified artifact or all artifacts with the specified group id. 
  This is configured using the `.csv` input file.
- Indexes include supported KMP platform list per artifact.
- Ranked search by `group-id` and `artifact-id` using the previously built index.
- GUI shell with the additional ability to filter search results by KMP platform.
- May be considered to be a performant solution within its limitations (right?).

## Usage
The application assembling and packaging is handled by _compose desktop_ gradle plugin, 
so an actual distribution can be created and installed. 
The examples below however use `./gradle run` command to remain focused on the usage. 

### Command Line
First the index has to be built with the specified allowlist file (see below). If the file is not specified,
 the [builtin config](src/main/kotlin/io/github/jeffset/mindexer/allowlist/AllowlistExampleGroupsImpl.kt) will be used.
```shell
./gradlew run --args="index --artifact-allowlist-file data/maven-kmp-libraries.csv"
```
This will create a file named `index.db` in the project directory.

Now one can search the index like this:
```shell
./gradlew run --args="search ktor"
```
### GUI

Assemble and start simple GUI by simply doing `./gradlew run` without arguments.
The builtin allowlist is used by default; to choose another one click `Pick Allowlist CSV`.
Indexing is done by manually clicking `Index`.
_The search results are dynamic and can be displayed gradually as indexing progresses_.

### Concerning CSV format

The file must be in Rfc4180 format (`\r\n` line separators). 
The header `namespace,name` must be the first entry.
Each `name` may be a literal artifact name (excluding KMP variants) or a `*` character.
The asterisk means "locate and resolve every artifact belonging to the specified namespace". 
Nested namespaces are not located automatically.

The example CSV file can be found in [data/maven-kmp-libraries.csv](data/maven-kmp-libraries.csv)

### Technologies used
| Area                           | Technology                                      |
|--------------------------------|-------------------------------------------------|
| Network Stack                  | ktor-client (okhttp)                            |
| Parallel Processing            | kotlinx-coroutines                              |
| Persistence                    | sqldelight (sqlite)                             |
| Serialization (xml, json, csv) | kotlinx-serialization (ktor and 3rd p. plugins) |
| CLI                            | kotlinx-cli                                     |
| GUI                            | Compose Desktop                                 |

As a general theme the technologies used can all be potentially used in a KMP project.

The full list of used libraries is under the `gradle/libs.versions.toml`. 

### What is not here
- User friendly error handling
- More advanced KMP search features.
- General stability and fidelity of the algorithms concerning Maven resolution.

