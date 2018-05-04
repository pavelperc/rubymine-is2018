# Test for arithmetical and logical operations


if not (1 + 2 > 2 * 2) or 5 > 2 * 5:  # should be true
    pass


if 4 // 2 // 2 == 1:  # should be True
    pass


if 5 // 2 == 1:  # should be true
    pass


# unary minus test
if -5 < 0:  # should be true
    pass

if 5 % 3 == 2:  # should be true
    pass

if (2 - 2) * 10 and 2 + 2:  # should be false
    pass


if 2 + 2 * 2 < (2 + 2) * 2:  # should be true
    pass