# Setting Up Gemini AI API

The ClassBridge app now uses Google's Gemini AI to provide intelligent, assignment-specific study help.

## Getting Your Gemini API Key

1. **Visit Google AI Studio**
   - Go to [https://makersuite.google.com/app/apikey](https://makersuite.google.com/app/apikey)
   - Sign in with your Google account

2. **Create an API Key**
   - Click "Create API Key"
   - Choose "Create API key in new project" or select an existing project
   - Copy the generated API key

## Adding the API Key to Your App

1. **Open the project `gradle.properties` file**
   - Navigate to `gradle.properties` in the project root

2. **Add these properties**
   ```properties
   GEMINI_API_KEY=AIzaSyD...your-actual-key-here
   AI_STUDY_BASE_URL=https://your-backend-api.com/
   ```

3. **Sync and rebuild the project**
   - Click "Sync Now" in Android Studio
   - Run `./gradlew clean installDebug`

4. **Do not commit secrets**
   - Keep real keys in a local/untracked configuration or CI secrets
   - If needed, use environment-based Gradle injection for release builds

## Security Note

⚠️ **IMPORTANT**: Never commit your API key to version control.

- Use local Gradle properties or environment variables for production
- For production apps, implement a backend service to securely manage API keys
- Monitor your API usage in the Google Cloud Console

## Features Using Gemini AI

The app now provides:

- **Assignment-Specific Help**: AI analyzes the assignment details (title, description, due date, points)
- **Material Context**: Selected assignment materials are included in the AI prompt
- **User Questions**: Students can ask specific questions about the assignment
- **Structured Responses**: AI returns organized study sections:
  - Assignment Overview
  - Key Concepts
  - Example Approach
  - Common Mistakes to Avoid
  - Getting Started Guide
- **Fallback Support**: If API fails, provides helpful fallback content

## Testing

After adding your API key:

1. Open the app and navigate to **Assignments**
2. Click on any assignment card
3. Click the **"Get AI Study Help"** button
4. (Optional) Select assignment materials to include
5. (Optional) Upload additional files
6. (Optional) Type a specific question
7. Click **"Get AI Study Help"**

The AI will analyze the assignment and provide comprehensive study guidance!

## Troubleshooting

**Error: "Failed to get AI help"**
- Check that your API key is correct
- Ensure you have internet connectivity
- Verify your API key has the Generative Language API enabled
- Check the Logcat for detailed error messages

**No response or timeout**
- Large assignments may take 10-20 seconds to process
- Check your internet connection speed
- Try with a shorter assignment or simpler question

**API Quota Exceeded**
- Free tier has rate limits
- Check your quota at [Google Cloud Console](https://console.cloud.google.com/)
- Consider upgrading for production use
