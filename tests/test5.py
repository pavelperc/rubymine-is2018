# Test for unknown variables

a = int(input())
b = int(input())
c = int(input())


# tests for searching of different comparisons:

if b > 7 or True:  # should be true
    pass

if b == (2 + 2) * 2 or 8 != b:  # should be true
    pass

if 7 + 7 < b and False:  # should be false
    pass


# another tests:

if a > 2 and b != 3 and c == 4 and b == 1 + 1 + 1:  # should be false
    pass

if a > 10 and not (a > 5):  # should be false
    pass

if a > 10 or a <= 10:  # should be true
    pass

if a > 10 or a < 10:  # should be nothing
    pass

if (a > 10 or b > 10) and a < 10:  # should be nothing
    pass


