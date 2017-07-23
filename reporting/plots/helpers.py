import seaborn
import sys
import six
import datetime
import matplotlib.backends.backend_pdf
import numpy as np
import pandas as pd
import matplotlib.pyplot as pp

def convertColumns(data, cols, func):
    newCols = data[cols].applymap(func)
    data1 = data.drop(cols, axis=1)
    return pd.merge(data1, newCols, left_index=True, right_index=True)
    
def todatetime(x):
    return np.datetime64(x)

def tohr(x):
    return x.to_datetime64().astype('datetime64[h]')

def tostr(x):
    return x.apply(str)

def formatCells(data):
    data = data.reset_index()
    data = data.round(3)
    for c in data.columns:
        if(data[c].dtype == 'datetime64[ns]'):
            data[c] = data[c].apply(lambda x: x.time())
    return data

def render_mpl_table(data, col_width=5.0, row_height=0.625, font_size=10,
                     header_color='#40466e', row_colors=['#f1f1f2', 'w'], edge_color='w',
                     bbox=[0, 0, 1, 1], header_columns=0, title = "",
                     fig=None, **kwargs):
    data = formatCells(data)
    if fig is None:
        size = (np.array(data.shape[::-1]) + np.array([0, 1])) * np.array([col_width, row_height])
        fig, ax = pp.subplots()
        ax.axis('off')

    mpl_table = ax.table(cellText=data.values, bbox=bbox, colLabels=data.columns, **kwargs)

    mpl_table.auto_set_font_size(False)
    mpl_table.set_fontsize(font_size)

    for k, cell in six.iteritems(mpl_table._cells):
        cell.set_edgecolor(edge_color)
        if k[0] == 0 or k[1] < header_columns:
            cell.set_text_props(weight='bold', color='w')
            cell.set_facecolor(header_color)
        else:
            cell.set_facecolor(row_colors[k[0]%len(row_colors) ])
    ax.set_title(title, fontsize = 16)
    return fig 