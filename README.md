This initial commit introduces some key features, as outlined below. As releases come out, they will be committed here. Note that this repository is for Android releases only - any releases for other platforms will be in a separate repository. While Android is the only supported platform as of now, I will include a link to the other repository platforms as they come out.

As this is still a small project, feel free to reach out to me at everett.hurder@gmail.com for any suggestions, improvements, or requests for future releases!

ALSO NOTE - for anyone looking to collaborate, I have included a markdown file as to how to generate a Gemini API key (yes, generated graciously by Gemini itself), and should you need Firebase credentials, I'd recommend you create your own Firebase project, add the Google Classroom API, download the google-service.json file, and upload it to the root of the repository. There's plenty of online resources... just look up "How to set up an Android Firebase project to authenticate with Google, and use the Google Classroom API, as well as Firestore and cloud messaging". will not be disclosing this file yet, as I am still working out all the authentication rules in Firebase.

Alright, the moment you have been waiting for... the features! I will list off features by screen below:

Dashboard

* Quick actions in the header.
* Overview of courses, assignments completed, and average grade.
* Upcoming assignments.
* Grade summary of each course.
* Active chat rooms.

**Assignments**

* Filter button in the top right corner to filter by course and status.
* List of assignments that fit the criteria by the filter (each assignment in this screen contains the name, description, points, attachments, status, due date, study assist button, which I will explain below, and teacher email option by pressing the email icon on the right of each assignment).
* AI study help button, which, when pressed, includes the assignment overview, key concepts, example approaches, mistakes to avoid, how to get started, and a button to ask another question about the assignment, as well as a toggleable navigation panel on the left side.
* A two-day report button, which gives you a quick rundown of assignments due within the next two days. This is geared for high school students, specifically those who do their homework the day it is assigned rather than the day before it is due (many high schools follow an alternating bell schedule, where periods 1, 2, 3, and 4 are on one day, and periods 5, 6, 7, 8 are on the other day).

**Grades**

* Overall average grade as a top banner for easy access.
* The average grade for each individual class.
* The assignment breakdown to examine the score of each individual assignment that contributes to the overall grade. This screen is located by pressing the dropdown on each course.
* Grade progression, which interpolates grades over time based on assignments as they are turned in and graded. This progression can prove useful if you are trying to see when your grade took a dip, and which assignment it was that caused it. This screen is viewed by pressing the dropdown on each course, then pressing the "View Grade Progression" button.

**Chat**

* Button to join a chat room given a room code in the top right corner.
* View of all enrolled chat rooms.
* The room code, amount of members, subject, name, description, and owner of the chat room. You may copy the room code by pressing the copy button next to the code in this screen. This screen can be opened by pressing a individual chat room and pressing the information button in the top right corner.
* The sender, content, and timestamp of each message (found by clicking the individual chat room and observing the messages sent)
* The text box, send button, and attachment button to send messages, with and without reference assignment materials attached.
* The back button to return to the main screen with all the chat rooms.
* The create room button in the bottom right corner, which asks for a room name and description. When created, the chat room will appear in the list of active chats.

**AI Study Assistant**

* Includes a file upload button in the top right corner. You may remove the file attached by pressing the "x" button in the top right corner, or select another file by pressing the paperclip in the top right corner, once you already have one selected.
* Example questions and AI disclaimer.
* Textbox and send button, which sends text and any attached materials to AI.
* Additionally note, this assistant has access to your classroom data - feel free to ask it something about your classes! It has access to assignments, materials, submissions, scores, etc.

**Profile**

* PII, such as your name and email in the top header.
* Statistics, such as the amount of courses enrolled in, total amount of assignments assigned, and average grade.
* Settings, such as push notifications, notification time, theme of the app, privacy terms, version status, and a sign-out button.
* To access these different unique views, you may press the navigation buttons in the bottom of the screen, or simply scroll left and right to access the adjacent screens for quick access.

Enjoy!
