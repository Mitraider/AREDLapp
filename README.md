# AREDLapp
This is a "fan-project", based on the All Rated Extreme Demons List website, that allows the users to view the list of a ranking of all the rated hardest levels from the game Geometry Dash.
( [Check the official AREDL website](https://aredl.net) )
I used their APIs to create an unofficial Android mobile port, 100% in kotlin (and XML, i don't think you can hard code your graphics x) )
What usually started as a small project has now gotten so much bigger with new features and updates still added by myself using only their documentation, their public APIs endpoints and my knowledge.

Being only in the first year of my Computer Science degree, I of course don't have all the knowledge to pull this off all by myself, so a little bit of AI was used, even though i tried using is as low as possible. It really helped to setup mainly the login via OAuth2, as getting and securing the tokens is difficult (for now, as I'm still not the best).

#Here are all the features integrated to this mobile port yet:
- A view of the levels of the demonlist, with their rankings. You can search any level you want, and click on its card to get a more detailed view of it, including the description given, the victors of the level, the packs the level is in etc.
- The full player leaderboard, with every roles the player has if he has any (AREDL+, developper, admin, and/or owner). You can search any player you want and access his profile to see all his completions, his country, his ranking etc.
- **ADDITION TO THE AREDL WEBSITE:** the "My Lists" tab: it contains 3 tabs (Favorites, To-Do, and completed). You can add any level at any time to your lists using the two buttons to the right of a level card, the star adding a level to the favorites tab, the "save" to add the level to the To-Do list. THe completed tab is automatically filled after you log into the app
- The Packs: In the AREDL website, some levels are grouped into packs worth more points if you complete every level from said pack. You can also access these packs, grouped by tier (Iron all the way up to Diamond), and click on one of them to see what it contains! When logged in, the app tracks the levels completed and increments the packs consequenlty to the levels done by the player.
- Mini-Games: I ported every mini game from the AREDL website to the app, which includes:
  - "Random Extreme Demon" which returns a random level within a range set by the player
  - "Extreme Demon Roulette": Get 1 more percent each run on a random level, all the way up to a full clear!
  - "Alphabet Challenge": Returns a level for the letters going from A to Z. If you complete a level for the whole alphabet, you win!
- The settings tab is accessible by clickng on the discord button in the top right corner (this UI design is **HIGHLY** subject to changes in later updates, and it's not very practical). In the settings tab, you can switch from light mode to dark mode (stay in dark mode please, the light mode is ugly as hell), and also change de secondary color using a color picker!
## Now the fun part:
- You can login the same way you do on AREDL, using the OAuth2 from AREDL discord app, as well as log out cause that seems quite... logical
- When logged in, you can get your leaderboard profile info through a button accessible via your pfp in the rop right corner.
- You can also access your submissions throught the "My Submissions" tab, submit and edit all your completions according to the [Submission guideline](https://aredl.net/guidelines) ruleset from the AREDL moderation team
- The "Completed" tab in "My Lists" is automatically filled with your completions.
- The "Packs" tab is automatically filled with your completions, and updated

# Requirements:
- An Android phone **UP TO DATE** or Bluestacks if you have an IPhone and still want to check out my project

# How to install:
- Head to the "Releases" tab on GitHub and get yourself the latest release (or an older one if you want to see how ugly it was before all the UI changes ^^')

# Known issues:
  - The section "Created by <creator>" doesn't display correctly and returns the default value.
 
# Special thanks:
- [Sphericle](https://github.com/sphericle), a head developper from the AREDL team, who helped me with some APIs endpoints, as well as giving me some infos to fill the submissions tab correctly.

Updates are still on the way, also feel free to check the source files of what I've done!
