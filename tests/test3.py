# Test for logical operators


if True and True:  # should be true
    pass

if True and False:  # should be false
    pass

if True or False:  # should be true
    pass

if False or 0:  # should be false
    pass

if 1 and True:  # should be true
    pass

if True and True:  # should be true
    pass

if not 5:  # should be false
    pass

if not 0:  # should be true
    pass

if (True or False) and True:  # should be true
    pass


if 3 > 4 and 5 < 10:  # should be false
    pass
elif not (3 > 4) or 5 > 10:  # should be true
    pass

