# Test for unknown variables

a = int(input())
b = int(input())
c = int(input())

if a > 10 and a < 5:  # should be false
    pass

if a > 10 or a <= 10:  # should be true
    pass

if a > 10 or a < 10:  # should be nothing
    pass


if (a > 10 or b > 10) and a < 10:  # should be nothing
    pass


