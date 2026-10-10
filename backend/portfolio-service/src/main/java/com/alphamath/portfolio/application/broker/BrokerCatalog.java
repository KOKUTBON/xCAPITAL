package com.alphamath.portfolio.application.broker;

import com.alphamath.portfolio.domain.broker.BrokerDefinition;
import com.alphamath.portfolio.domain.broker.BrokerIntegrationStatus;
import com.alphamath.portfolio.domain.execution.AssetClass;
import com.alphamath.portfolio.domain.execution.OrderType;
import com.alphamath.portfolio.domain.execution.Region;
import com.alphamath.portfolio.domain.execution.TimeInForce;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BrokerCatalog {
  private final List<BrokerDefinition> definitions = build();

  public List<BrokerDefinition> list() {
    return new ArrayList<>(definitions);
  }

  public BrokerDefinition find(String brokerId) {
    for (BrokerDefinition def : definitions) {
      if (def.getId().equalsIgnoreCase(brokerId)) return def;
    }
    return null;
  }

  private List<BrokerDefinition> build() {
    List<BrokerDefinition> out = new ArrayList<>();
    out.add(def("interactive_brokers", "Interactive Brokers", 95,
        List.of(Region.US, Region.EU, Region.UK, Region.APAC, Region.LATAM, Region.GLOBAL),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.FX, AssetClass.FIXED_INCOME,
            AssetClass.OPTIONS, AssetClass.FUTURES, AssetClass.COMMODITIES),
        List.of("multi_asset", "global_access", "options", "fx", "futures")));
    out.add(def("schwab", "Charles Schwab", 90,
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.FIXED_INCOME, AssetClass.MUTUAL_FUND, AssetClass.OPTIONS),
        List.of("retirement", "research", "options")));
    out.add(def("fidelity", "Fidelity", 90,
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.FIXED_INCOME, AssetClass.MUTUAL_FUND, AssetClass.OPTIONS),
        List.of("retirement", "options", "fractional")));
    out.add(def("jp_morgan", "JP Morgan", 88,
        List.of(Region.US, Region.EU, Region.UK),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.FIXED_INCOME, AssetClass.MUTUAL_FUND, AssetClass.OPTIONS),
        List.of("managed_portfolios", "banking", "research", "fractional", "options", "instant_transfer")));
    out.add(def("robinhood", "Robinhood", 82,
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.OPTIONS, AssetClass.CRYPTO),
        List.of("fractional", "options", "crypto")));
    out.add(def("alpaca", "Alpaca", 80,
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF),
        List.of("api_first", "paper_trading")));
    out.add(def("tradier", "Tradier", 78,
        List.of(Region.US),
        List.of(AssetClass.EQUITY, AssetClass.ETF, AssetClass.OPTIONS),
        List.of("options", "broker_api")));
    out.add(def("coinbase", "Coinbase", 85,
        List.of(Region.GLOBAL),
        List.of(AssetClass.CRYPTO),
        List.of("custody", "staking")));
    out.add(def("binance", "Binance", 86,
        List.of(Region.GLOBAL),
        List.of(AssetClass.CRYPTO),
        List.of("spot", "derivatives")));
    out.add(def("kraken", "Kraken", 84,
        List.of(Region.GLOBAL),
        List.of(AssetClass.CRYPTO),
        List.of("spot", "staking")));
    out.add(def("oanda", "OANDA", 83,
        List.of(Region.GLOBAL),
        List.of(AssetClass.FX),
        List.of("fx", "api_first")));
    out.add(def("fxcm", "FXCM", 81,
        List.of(Region.GLOBAL),
        List.of(AssetClass.FX),
        List.of("fx", "api_first")));
    return out;
  }

  private BrokerDefinition def(String id, String name, int score,
                               List<Region> regions, List<AssetClass> assets, List<String> features) {
    BrokerDefinition def = new BrokerDefinition();
    def.setId(id);
    def.setName(name);
    def.setRegions(new ArrayList<>(regions));
    def.setAssetClasses(new ArrayList<>(assets));
    def.setOrderTypes(List.of(OrderType.MARKET, OrderType.LIMIT, OrderType.STOP, OrderType.STOP_LIMIT));
    def.setTimeInForce(List.of(TimeInForce.DAY, TimeInForce.GTC, TimeInForce.IOC, TimeInForce.FOK));
    def.setFeatures(new ArrayList<>(features));
    def.setScore(score);
    def.setIntegrationStatus(BrokerIntegrationStatus.PENDING_KEYS);
    def.setNotes("Integration scaffold. Live connectivity requires broker approvals and credentials.");
    return def;
  }
}
