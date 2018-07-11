package com.cybexmobile.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.cybexmobile.R;
import com.cybexmobile.activity.OwnOrderHistoryActivity;
import com.cybexmobile.adapter.viewholder.EmptyViewHolder;
import com.cybexmobile.data.item.OpenOrderItem;
import com.cybexmobile.fragment.data.WatchlistData;
import com.cybexmobile.graphene.chain.AssetObject;
import com.cybexmobile.graphene.chain.BlockHeader;
import com.cybexmobile.graphene.chain.LimitOrderObject;
import com.cybexmobile.graphene.chain.OrderHistory;
import com.cybexmobile.utils.AssetUtil;
import com.cybexmobile.utils.DateUtils;

import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;

public class OwnOrderHistoryRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final static int TYPE_EMPTY = 0;
    private final static int TYPE_CONTENT = 1;

    private List<OwnOrderHistoryActivity.OrderHistoryItem> mOrderHistoryItems;
    private Context mContext;

    public OwnOrderHistoryRecyclerViewAdapter(Context context, List<OwnOrderHistoryActivity.OrderHistoryItem> orderHistoryItems) {
        mOrderHistoryItems = orderHistoryItems;
        mContext = context;
    }

    public void setOrderHistoryItems(List<OwnOrderHistoryActivity.OrderHistoryItem> orderHistoryItems){
        mOrderHistoryItems = orderHistoryItems;
        notifyDataSetChanged();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        if(viewType == TYPE_EMPTY){
            view = LayoutInflater.from(mContext).inflate(R.layout.item_empty, parent, false);
            return new EmptyViewHolder(view);
        }
        view = LayoutInflater.from(mContext).inflate(R.layout.item_own_order_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if(holder instanceof EmptyViewHolder){
            EmptyViewHolder emptyViewHolder = (EmptyViewHolder) holder;
            emptyViewHolder.mTvEmpty.setText(mContext.getResources().getString(R.string.text_no_exchange_history));
            return;
        }
        ViewHolder viewHolder = (ViewHolder) holder;
        OwnOrderHistoryActivity.OrderHistoryItem orderHistoryItem = mOrderHistoryItems.get(position);
        AssetObject base = orderHistoryItem.baseAsset;
        AssetObject quote = orderHistoryItem.quoteAsset;
        BlockHeader block = orderHistoryItem.block;
        if (base != null && quote != null) {
            if ((!base.symbol.startsWith("CYB") && !base.symbol.startsWith("JADE")) ||
                    (!quote.symbol.startsWith("CYB") && !quote.symbol.startsWith("JADE"))) {
                RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                layoutParams.height = 0;
                layoutParams.width = 0;
                holder.itemView.setVisibility(View.GONE);
            } else {
                RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                layoutParams.height = RecyclerView.LayoutParams.WRAP_CONTENT;
                layoutParams.width = RecyclerView.LayoutParams.WRAP_CONTENT;
                holder.itemView.setVisibility(View.VISIBLE);
                String quoteSymbol = quote.symbol.contains("JADE") ? quote.symbol.substring(5, quote.symbol.length()) : quote.symbol;
                String baseSymbol = base.symbol.contains("JADE") ? base.symbol.substring(5, base.symbol.length()) : base.symbol;
                viewHolder.mTvBuySell.setText(mContext.getResources().getString(orderHistoryItem.isSell ? R.string.open_order_sell : R.string.open_order_buy));
                viewHolder.mTvBuySell.setBackground(mContext.getResources().getDrawable(orderHistoryItem.isSell ?R.drawable.bg_btn_sell : R.drawable.bg_btn_buy));
                viewHolder.mTvBaseSymbol.setText(baseSymbol);
                viewHolder.mTvQuoteSymbol.setText(quoteSymbol);
                double baseAmount;
                double quoteAmount;
                if(orderHistoryItem.isSell){
                    baseAmount = orderHistoryItem.orderHistory.receives.amount / Math.pow(10, base.precision);
                    quoteAmount = orderHistoryItem.orderHistory.pays.amount / Math.pow(10, quote.precision);
                }else {
                    baseAmount = orderHistoryItem.orderHistory.pays.amount / Math.pow(10, base.precision);
                    quoteAmount = orderHistoryItem.orderHistory.receives.amount / Math.pow(10, quote.precision);
                }
                /**
                 * fix bug:CYM-371
                 * 价格保留8位小数
                 */
                viewHolder.mTvBasePrice.setText(String.format(Locale.US, "%.8f %s", baseAmount/quoteAmount, baseSymbol));
                viewHolder.mTvBaseAmount.setText(String.format("%." + base.precision + "f %s", baseAmount, baseSymbol));
                viewHolder.mTvQuoteAmount.setText(String.format("%." + quote.precision +"f %s", quoteAmount, quoteSymbol));
                if(block != null){
                    viewHolder.mTvTime.setText(DateUtils.formatToDate(DateUtils.PATTERN_MM_dd_HH_mm_ss, DateUtils.formatToMillis(block.timestamp)));
                }
            }
        }else{
                RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) holder.itemView.getLayoutParams();
                layoutParams.height = 0;
                layoutParams.width = 0;
                holder.itemView.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return mOrderHistoryItems == null || mOrderHistoryItems.size() == 0 ? 1 : mOrderHistoryItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return mOrderHistoryItems == null || mOrderHistoryItems.size() == 0 ? TYPE_EMPTY : TYPE_CONTENT;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.item_own_order_history_tv_buy_sell)
        TextView mTvBuySell;
        @BindView(R.id.item_own_order_history_tv_quote_symbol)
        TextView mTvQuoteSymbol;
        @BindView(R.id.item_own_order_history_tv_base_symbol)
        TextView mTvBaseSymbol;
        @BindView(R.id.item_own_order_history_tv_base_amount)
        TextView mTvBaseAmount;
        @BindView(R.id.item_own_order_history_tv_base_price)
        TextView mTvBasePrice;
        @BindView(R.id.item_own_order_history_tv_quote_amount)
        TextView mTvQuoteAmount;
        @BindView(R.id.item_own_order_history_tv_time)
        TextView mTvTime;

        ViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }
}