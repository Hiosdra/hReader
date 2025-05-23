# GitHub Copilot Instructions for hReader

This document outlines coding preferences and guidelines for the hReader project when using GitHub Copilot.

## Code Style Preferences

### Documentation

- **NO JAVADOCS**: Do not generate or include Javadoc-style comments in the code
- **NO INLINE COMMENTS**: Avoid adding inline comments to the code
- Use meaningful method and variable names instead of comments or documentation

### Kotlin Specific

- Follow Kotlin idioms and best practices
- Use Kotlin's concise syntax features (extension functions, smart casts, etc.)
- Prefer immutability where appropriate (use `val` over `var` when possible)

### Architecture

- Follow MVVM architecture pattern
- Separate concerns between data, domain, and presentation layers
- Use Koin for dependency injection (modules are defined in `app/src/main/java/com/hiosdra/hreader/di/appModule.kt` and `networkModule.kt`)

### Android Best Practices

- Follow Android architecture components guidelines
- Prefer Kotlin Coroutines for asynchronous operations
- Use Jetpack Compose for UI construction

## Security

- Handle sensitive data appropriately
- Don't hardcode credentials or API keys
