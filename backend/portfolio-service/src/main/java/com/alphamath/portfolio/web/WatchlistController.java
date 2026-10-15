package com.alphamath.portfolio.web;

import com.alphamath.portfolio.application.watchlist.WatchlistService;
import com.alphamath.portfolio.domain.watchlist.Watchlist;
import com.alphamath.portfolio.domain.watchlist.WatchlistItem;
import com.alphamath.portfolio.domain.watchlist.WatchlistItemRequest;
import com.alphamath.portfolio.domain.watchlist.WatchlistRequest;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlists")
public class WatchlistController {
  private final WatchlistService watchlists;

  public WatchlistController(WatchlistService watchlists) {
    this.watchlists = watchlists;
  }

  @PostMapping
  public Watchlist create(@RequestBody WatchlistRequest req, Principal principal) {
    return watchlists.create(userId(principal), req);
  }

  @GetMapping
  public List<Watchlist> list(Principal principal) {
    return watchlists.list(userId(principal));
  }

  @PostMapping("/{id}")
  public Watchlist update(@PathVariable String id, @RequestBody WatchlistRequest req, Principal principal) {
    return watchlists.update(userId(principal), id, req);
  }

  @DeleteMapping("/{id}")
  public void delete(@PathVariable String id, Principal principal) {
    watchlists.delete(userId(principal), id);
  }

  @GetMapping("/{id}/items")
  public List<WatchlistItem> items(@PathVariable String id, Principal principal) {
    return watchlists.listItems(userId(principal), id);
  }

  @PostMapping("/{id}/items")
  public WatchlistItem addItem(@PathVariable String id, @RequestBody WatchlistItemRequest req, Principal principal) {
    return watchlists.addItem(userId(principal), id, req);
  }

  @DeleteMapping("/{id}/items/{itemId}")
  public void removeItem(@PathVariable String id, @PathVariable String itemId, Principal principal) {
    watchlists.removeItem(userId(principal), id, itemId);
  }

  @PostMapping("/{id}/insights")
  public List<WatchlistItem> refreshInsights(@PathVariable String id, @RequestBody InsightRequest req, Principal principal) {
    int horizon = req == null ? 1 : req.horizon;
    int lookback = req == null ? 120 : req.lookback;
    return watchlists.refreshInsights(userId(principal), id, horizon, lookback);
  }

  private String userId(Principal principal) {
    return principal == null ? "unknown" : principal.getName();
  }

  public static class InsightRequest {
    public int horizon = 1;
    public int lookback = 120;
  }
}
