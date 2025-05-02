# Orion AI - Android Chatbot

A modern Android chatbot application that uses the Groq API for AI-powered conversations.

## Features

- Clean and modern Material Design UI
- Real-time chat with AI
- Speech-to-text input
- Text-to-speech output
- Copy and share responses
- Dark mode support

## Setup

1. Clone the repository
2. Create a `local.properties` file in the root directory (you can copy from `local.properties.template`)
3. Add your Android SDK path to `local.properties`:
   ```
   sdk.dir=YOUR_ANDROID_SDK_PATH
   ```
4. Get your Groq API key from [Groq's website](https://console.groq.com/)
5. Add your Groq API key to `local.properties`:
   ```
   GROQ_API_KEY=YOUR_GROQ_API_KEY
   ```
6. Open the project in Android Studio
7. Build and run the application

## Security Note

Never commit your `local.properties` file or share your API key. The `local.properties` file is already in `.gitignore` to prevent accidental commits.

## Requirements

- Android Studio Arctic Fox or newer
- Android SDK 21 or higher
- Groq API key

## License

This project is licensed under the MIT License - see the LICENSE file for details. 