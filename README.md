# WatchKeyboard
An Android Wear keyboard app with n-gram character prediction and correction

This is a basic app-based keyboard for Android wear intended as a base for research projects.

The base keyboard is an app for Android Wear and is styled for a round watch. It uses a 7-gram
character language model that is fairly simple but good for focussed studies. The dictionary
is relatively small but gives a good flavour. Augmenting the dictionary with the phrases of the 
test set helps prediction without overly focussing the keyboard on those phrases (users still
have to type accurately but prediction is very high quality simulating a much more impressive
language and context model).

The package includes the wear app, a mobile app and a shared package where most of the modelling
lives to allow for use on either wear or mobile. The wear app can send messages to the phone app
that are displayed on screen just now but could easily be stored in a log file.

V1 is an initial release that is based on ripping out research project related code - hopefully
without too many entrails left.

Code distributed under MIT licence for open sharing.
