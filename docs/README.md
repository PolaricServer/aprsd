# Polaric APRSD Documentation

This directory contains documentation files for Polaric APRSD in reStructuredText (RST) format, suitable for use with ReadTheDocs. Files may be moved to the docs repository and be visible on the documentation on readthedocs. 

## Contents

- `aprsis-config.rst` - Complete guide to configuring and using the APRS-IS service
- `aprs-filters.rst` - Comprehensive guide to APRS-IS filters implemented in Polaric APRSD

## Format

The documentation is written in reStructuredText (RST) format, which is the standard for ReadTheDocs and Sphinx documentation systems.

## Contributing

When adding new documentation:

1. Use `.rst` extension for reStructuredText files
2. Follow the existing structure and formatting conventions
3. Include practical examples where applicable
4. Keep technical accuracy by referencing the source code

## Building Documentation

To build the documentation locally with Sphinx:

```bash
pip install sphinx
cd docs
sphinx-build -b html . _build
```

For ReadTheDocs integration, a `conf.py` configuration file and `index.rst` would typically be added.
