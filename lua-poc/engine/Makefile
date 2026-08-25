# Lua node-navigation engine (Task 6 of the Lua mapping-engine feasibility
# project). See
# .superpowers/sdd/2026-08-25-lua-mapping-engine-deepdive/task-6-brief.md
#
# The target host (Task 7/8) is gopher-lua, a Lua 5.1 dialect. `make test`
# runs the busted suite under a real Lua 5.1 interpreter (not whatever
# `lua`/`busted` happen to resolve to by default) so a 5.2+-only construct
# fails here instead of only surfacing under gopher-lua later.
#
# One-time setup, if `lua5.1` exists but busted isn't installed for it:
#   luarocks --lua-version=5.1 install busted --local --tree=$$HOME/.luarocks-51

LUAROCKS_TREE ?= $(HOME)/.luarocks-51

.PHONY: test
test:
	@eval "$$(luarocks --lua-version=5.1 --tree=$(LUAROCKS_TREE) path)"; \
	PATH="$(LUAROCKS_TREE)/bin:$$PATH" busted --lua=lua5.1 spec/
