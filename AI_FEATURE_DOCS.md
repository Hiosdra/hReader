# AI Article Overview Feature

The hReader app now includes an AI-powered article overview feature using OpenRouter's free models. This feature allows users to generate concise summaries of articles using various AI models.

## Features

### AI Models Available
- **Llama 3.2 3B**: Fast and efficient for summaries (default)
- **Llama 3.2 1B**: Lightweight model for basic summaries  
- **Qwen 2.5 1.5B**: Good balance of speed and quality
- **Gemma 2 2B**: Google's efficient model for text generation

All models are free to use via OpenRouter's API.

### User Interface

#### Settings Screen
- Navigate to Settings to select your preferred AI model
- Each model includes a description of its characteristics
- Settings are persisted across app sessions

#### Article Reader
- **AI Overview Button**: Star icon (⭐) in the top toolbar
- Click to generate an AI summary of the current article
- Loading indicator shows generation progress
- Overview appears in a card format below the article metadata

### Technical Implementation

#### Architecture
- `AiModel` enum defines available models with display names and IDs
- `ArticleAiService` handles OpenRouter API integration
- `OpenRouterApiService` provides Retrofit-based API client
- Extended `PreferencesManager` for model selection persistence

#### Error Handling
- Network failure handling with user-friendly error messages
- Rate limiting protection (API-level)
- Graceful degradation when AI service is unavailable

#### Performance
- Caches generated overviews per article session
- Limits article content to 3000 characters for token efficiency
- Cleans HTML tags from content before sending to AI

## Usage

1. **Setup**: No additional setup required - uses built-in API key
2. **Select Model**: Go to Settings > AI Model for Article Overview
3. **Generate Overview**: In any article, tap the star icon ⭐
4. **View Summary**: AI-generated overview appears below article title

## API Integration

Uses OpenRouter.ai unified API with:
- Base URL: `https://openrouter.ai/api/v1/`
- Authentication: Bearer token in headers
- Content-Type: `application/json`
- Custom headers for app identification

## Privacy & Usage

- Article content is sent to OpenRouter's servers for processing
- No content is stored permanently by the AI service
- All models used are free tier with reasonable rate limits
- Complies with OpenRouter's terms of service

## Rate Limits

Free models on OpenRouter have generous rate limits for personal use:
- Requests are automatically managed by the API
- Error handling provides feedback if limits are exceeded
- No client-side rate limiting implemented (relies on API)

## Future Enhancements

Potential improvements for future versions:
- Local caching of overviews to reduce API calls
- Client-side rate limiting for better UX
- Additional model options as they become available
- Customizable prompt templates for different summary styles