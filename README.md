# set-card-game
Set Card Game is a Java-based multiplayer card game with AI opponents, featuring multi-threading for efficient processing. Find sets of matching or different features in this fast-paced game.
# Set Card Game
 
**Getting Started**

**Prerequisites**
Make sure Java is installed (1.8 or later).

**Clone or Download**
Clone or download the repository to your local machine.

**Configuration**
Edit the configuration file (at `/src/main/resources/config.properties`) as you wish - you should edit the following fields:

- `HumanPlayers` - Amount of human players.
- `ComputerPlayers` - Amount of computer players.
- `Hints` - True/False - show legal sets currently visible on the table.
- `PlayerNames` - players names, separated by ", " - example is given.
- `TurnTimeoutSeconds`:
  - by setting `TurnTimeoutSeconds > 0` (60 by default), the game will start in regular countdown timer mode (and you can set its duration).
  - by setting `TurnTimeoutSeconds = 0`, the game will start in a "Free play" mode, without any timer.
  - by setting `TurnTimeoutSeconds < 0`, the game will start in "Elapsed" mode, where the timer resets after a legal set is collected.

**Compilation**
To compile: `mvn clean compile test`

**Running the Game**
To run: `java -cp target/classes bguspl.set.Main`

**Keyboard & Interface**

![Screenshot 1](https://user-images.githubusercontent.com/109943831/218310054-1a63cc6f-a86d-478e-be11-0a45419e7c8c.png)

![Screenshot 2](https://user-images.githubusercontent.com/109943831/218310096-55f31b0c-98f0-4e32-b991-725de975c1db.png)

**Gameplay**

- The goal of the game is to find as many sets as possible before the deck runs out.
- A set is defined as three cards where each feature is either all the same or all different for all three cards.
- Features of each card:
  - Color
  - Shape
  - Number
  - Fill

![Screenshot 3](https://user-images.githubusercontent.com/109943831/218310141-3e4f902f-acb1-4453-9b8a-fa2275e2c849.png)

![Screenshot 4](https://user-images.githubusercontent.com/109943831/218310141-3e4f902f-acb1-4453-9b8a-fa2275e2c849.png)
