AndroidDESCracker
=================

_Co-authored by Hoi Kit Goodwin Lam._<br />
A distributed DES cracker and English text recogniser designed for the Nexus 7. Should also work on sufficiently recent
Android devices (min SDK version: 14).

##Notable Classes
###TextRecogniser Class
Contains English text recognising logic based upon recognising words from the USA word dictionary gotten from the
GNU aspell dictionaries. Word recognising functionality implemented by comparing found words amongst various other
short-cuts.

###DES Class
Data Encryption Standard implementation written by David Simmons. __Not__ the same module as residing in my JavaSE 7
DataEncryptionStandard repository, although this implementation still adheres standards specified in FIPS 46-3.

##Issues
Brute force cracking is rather slow. This is mostly attributed to the naive way of recognising English text (could be
much further improved via adding pre-processing functionality to short-circuit text strings that do not contain valid
English characters).
