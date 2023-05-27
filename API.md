# API

API for accessing information about Sponge downloads

Root URL: https://dl-api.spongepowered.org/

* [/v2/groups](#v2groups)
* [/v2/groups/\<groupCoordinates>/artifacts](#v2groupsgroupcoordinatesartifacts)
* [/v2/groups/\<groupCoordinate>/artifacts/\<artifactId>](#v2groupsgroupcoordinateartifactsartifactid)
* [/v2/groups/\<groupCoordinate>/artifacts/\<artifactId>/versions](#v2groupsgroupcoordinateartifactsartifactidversions)

### /v2/groups
Lists available groups.

Example: GET https://dl-api.spongepowered.org/v2/groups
```json
{
    "type": "Groups",
    "groups": [
        {
            "groupCoordinates": "org.spongepowered",
            "name": "SpongePowered",
            "website": "https://spongepowered.org/"
        }
    ]
}
```

### /v2/groups/\<groupCoordinates>/artifacts
List available artifacts for a group.

Example: GET https://dl-api.spongepowered.org/v2/groups/org.spongepowered/artifacts
```json
{
    "type": "Artifacts",
    "artifactIds": [
        "spongevanilla",
        "spongeforge"
    ]
}
```

### /v2/groups/\<groupCoordinate>/artifacts/\<artifactId>
List metadata for an artifact, including available tags.

Example:
GET https://dl-api.spongepowered.org/v2/groups/org.spongepowered/artifacts/spongeforge
```json
{
    "type": "latest",
    "coordinates": {
        "groupId": "org.spongepowered",
        "artifactId": "spongeforge"
    },
    "displayName": "SpongeForge",
    "website": null,
    "gitRepository": "https://github.com/SpongePowered/SpongeForge",
    "issues": null,
    "tags": {
        "api": [
            "8.1",
            "7.4",
            "..."
        ],
        "forge": [
            "2838",
            "36.2.5",
            "..."
        ],
        "minecraft": [
            "1.16.5",
            "..."
        ]
    }
}
```

### /v2/groups/\<groupCoordinate>/artifacts/\<artifactId>/versions
List available versions for an artifact. This list can be filtered based on URL parameters.

URL Parameters:
- `tags`: This is a dynamic list of values an artifact may be tagged with. This follows a `key:value` mapping, with
  multiple tags being requested seperated by a comma. Example tags include minecraft and api. Example: `minecraft:1.16.5`
  or `minecraft:1.16.5,api:8.1`. By default, no tags are filtered.
- `recommended`: Whether to only include recommended builds. By default, this is `false`.

Example: GET https://dl-api.spongepowered.org/v2/groups/org.spongepowered/artifacts/spongeforge/versions?recommended=true&tags=minecraft:1.12.2,api:7.4
```json
{
    "artifacts": {
        "1.12.2-2838-7.4.7": {
            "tagValues": {
                "minecraft": "1.12.2",
                "forge": "2838",
                "api": "7.4"
            },
            "recommended": true
        },
        "1.12.2-2838-7.4.6": {
            "tagValues": {
                "minecraft": "1.12.2",
                "forge": "2838",
                "api": "7.4"
            },
            "recommended": true
        },
        "1.12.2-2838-7.4.5": {
            "tagValues": {
                "minecraft": "1.12.2",
                "forge": "2838",
                "api": "7.4"
            },
            "recommended": true
        },
        "...": {}
    },
    "offset": 0,
    "limit": 25,
    "size": 8
}
```
