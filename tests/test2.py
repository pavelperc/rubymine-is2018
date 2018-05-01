# Test for comparison operators

if 2 == 2:  # should be true
    pass

if 2 != 2:  # should be false
    pass

if 1 < 2:  # should be true
    pass

if 1 > 2:  # should be false
    pass

if 1 >= 1:  # should be true
    pass

if 1 >= 2:  # should be false
    pass


if 5 <= 5:  # should be true
    pass

if 5 <= 6:  # should be true
    pass

# test for BigInteger
if 99999999999999999999999999 < 1000000000000000000000000000000000:  # should be true (and no one exception)
    pass

# test for BigInteger
if 99999999999999999999999999 >= 1000000000000000000000000000000000:  # should be false
    pass

