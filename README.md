# CSON Converter

<p>
A REST API service for converting structured data files between **JSON↔XML↔CSV** formats,
with support for applying patterns during conversion.
</p>

<p>
<b>Patterns</b> are basically a list of modifications they consist of. <b>Modifications</b> are specific settings,
which allows you to update, add or delete fields in converter file during conversion.

To use patterns you have to be authenticated and verified (your email must be verified).
</p>

---

## Patterns

A pattern is a named, reusable set of modifications that is applied to every record in a file during conversion.
You create a pattern and then select it in the main page while attaching a file for conversion — 
the converter runs all its modifications on each row before writing the output.

Patterns belong to you: only you can see, edit, or delete your own patterns.

---

## Modifications

A modification is a single field-level rule inside a pattern. Each modification targets a field by its original name.
Modifications can only be added or updated through their parent pattern.

**Table of all possible correct modifications**

| What it does                                    | Old Field Name | New Field Name | New Field Value | New Field Type |
|-------------------------------------------------|----------------|----------------|-----------------|----------------|
| Add a new field                                 | -              | +              | -               | -              |
| Add a new field with a new value                | -              | +              | +               | -              |
| Add a new field with a new type                 | -              | +              | -               | +              |
| Add a new field with a new value and a new type | -              | +              | +               | +              |
| Update value of a field                         | +              | -              | +               | -              |
| Update type of a field                          | +              | -              | -               | +              |
| Update type and value of a field                | -              | +              | +               | +              |
| Rename a field                                  | +              | +              | -               | -              |
| Rename and update value of a field              | +              | +              | +               | -              |
| Rename and update type of a field               | +              | +              | -               | +              |
| Rename and update value and type of a field     | +              | +              | +               | +              |
| Delete a field                                  | +              | -              | -               | -              |

Note, that all possible values for New Field Type are (`Integer`, `Float`, `Boolean`) and `String` by default.

**Table of all possible incorrect modifications**

| Old Field Name | New Field Name | New Field Value | New Field Type |
|----------------|----------------|-----------------|----------------|
| -              | -              | -               | -              |
| -              | -              | +               | -              |
| -              | -              | -               | +              |
| -              | -              | +               | +              |

All these exceptions will cause `400 Bad Request` while conversion. This means that you potentially can create patterns
with such modifications or update existing patterns with them, but it is meaningless since you cannot use it.

---

## Full Project Documentation

Full project documentation — request/response schemas, technical details and architecture overview:

**[docs/PROJECT.md](docs/PROJECT.md)**