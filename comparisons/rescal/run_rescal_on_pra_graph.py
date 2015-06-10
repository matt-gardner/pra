#!/usr/bin/env python

import logging
logging.basicConfig(level=logging.INFO)
_log = logging.getLogger('Example Kinships')

import numpy as np
from collections import defaultdict
from scipy.sparse import dok_matrix
from rescal import rescal_als


class Dictionary(object):
    def __init__(self):
        self.strings = dict()
        # Confusing, maybe, but I would like this to be 1-indexed, not
        # 0-indexed
        self.array = [-1]
        self.current_index = 1

    def getIndex(self, string, _force_add=False):
        if not _force_add:
            index = self.strings.get(string, None)
            if index:
                return index
        self.strings[string] = self.current_index
        self.array.append(string)
        self.current_index += 1
        return self.current_index - 1

    def getString(self, index):
        return self.array[index]

    def getAllStrings(self):
        return self.array[1:]

    def save_to_file(self, f):
        for i, string in enumerate(self.array):
            if i == 0: continue
            f.write('%d\t%s\n' % (i, string))

    @classmethod
    def read_from_file(cls, f):
        """Initialize an index object from a file.  Assumes there are no
        missing indices, and that the first entry is 1."""
        index = cls()
        if type(f) == str:
            f = open(f)
        for line in f:
            try:
                _, string = line.strip().split('\t')
            except ValueError:
                print 'Offending line:', line
                raise
            index.getIndex(string, _force_add=True)
        return index

def try_makedirs(path, die_on_exist=False):
    """Do the equivalent of mkdir -p."""
    import os
    try:
        os.makedirs(path)
    except OSError, e:
        import errno
        if die_on_exist or e.errno != errno.EEXIST:
            raise


def read_tensor_from_graph(graph_dir):
    node_dict = Dictionary.read_from_file(graph_dir + 'node_dict.tsv')
    num_nodes = node_dict.current_index - 1

    edge_dict = Dictionary.read_from_file(graph_dir + 'edge_dict.tsv')
    num_relations = edge_dict.current_index - 1

    print 'Num nodes:', num_nodes
    print 'Num relations:', num_relations
    total_possible_size = num_nodes * num_nodes * num_relations
    print 'Total tensor size:', total_possible_size

    T = [(edge_dict.getString(i+1),
          dok_matrix((num_nodes, num_nodes), np.float32))
         for i in range(num_relations)]
    print 'Reading graph'
    for line in open(graph_dir + 'graph_chi/edges.tsv'):
        fields = line.split('\t')
        source = int(fields[0]) - 1
        target = int(fields[1]) - 1
        relation = int(fields[2]) - 1
        T[relation][1][(source, target)] = 1
    num_nonzero = sum([len(t[1].keys()) for t in T])
    print 'Number of non-zeros:', num_nonzero
    print 'Percent filled:', float(num_nonzero) / total_possible_size

    T.sort()
    return T, node_dict, edge_dict


def create_test_tensor():
    node_dict = Dictionary()
    node_dict.getIndex('n1')
    node_dict.getIndex('n2')
    node_dict.getIndex('n3')
    node_dict.getIndex('n4')
    num_nodes = node_dict.current_index - 1

    edge_dict = Dictionary()
    edge_dict.getIndex('e1')
    edge_dict.getIndex('e2')
    edge_dict.getIndex('e3')
    edge_dict.getIndex('e4')
    num_relations = edge_dict.current_index - 1

    print 'Num nodes:', num_nodes
    print 'Num relations:', num_relations
    print 'Total tensor size:', num_nodes * num_nodes * num_relations

    T = [dok_matrix((num_nodes, num_nodes), np.float32) for i in range(num_relations)]
    T[0][0,0] = 1
    T[0][0,1] = 1
    T[0][0,2] = 1
    T[0][0,3] = 1
    T[1][0,0] = 1
    T[1][0,1] = 1
    T[1][0,2] = 1
    T[1][0,3] = 1
    T[2][0,0] = 1
    T[2][0,1] = 1
    T[2][0,2] = 1
    T[2][0,3] = 1
    T[3][0,0] = 1
    T[3][0,1] = 1
    T[3][0,2] = 1
    print 'Number of non-zeros:', sum([len(t.keys()) for t in T])

    return T, node_dict, edge_dict

def run_rescal(graph_dir, out_dir):
    try_makedirs(out_dir)

    T, node_dict, edge_dict = read_tensor_from_graph(graph_dir)
    #T, node_dict, edge_dict = create_test_tensor()

    print 'Samples from T[0] and T[1]:'
    import random
    t1_keys = T[0][1].keys()
    random.shuffle(t1_keys)
    print 'Relation for T[0]:', T[0][0]
    print 'Number of keys for T[0]:', len(t1_keys)
    for i in range(10):
        print '  ', t1_keys[i]
    t2_keys = T[1][1].keys()
    random.shuffle(t2_keys)
    print 'Relation for T[1]:', T[1][0]
    print 'Number of keys for T[1]:', len(t2_keys)
    for i in range(10):
        print '  ', t2_keys[i]
    print 'Size of intersection:', len(set(t1_keys).intersection(set(t2_keys)))

    print 'Running RESCAL code'
    rank = 20
    maxIter = 100
    conv = 1e-5
    lambda_A = 0
    lambda_R = 0
    A, R, _, _, _ = rescal_als(
            [t[1].copy() for t in T],
            rank,
            init='nvecs',
            conv=conv,
            lambda_A=lambda_A,
            lambda_R=lambda_R,
            maxIter=maxIter,
            )

    print 'A shape:', A.shape
    print 'R[0] shape:', R[0].shape
    print 'R size:', len(R)

    print '\nWriting A and R to disk'
    a_matrix_file = out_dir + 'a_matrix.tsv'
    r_matrix_file = out_dir + 'r_matrix.tsv'
    out = open(a_matrix_file, 'w')
    for n in range(node_dict.current_index - 1):
        node = node_dict.getString(n+1)
        out.write(node)
        for j in range(rank):
            out.write('\t')
            out.write(str(A[n,j]))
        out.write('\n')
    out.close()
    out = open(r_matrix_file, 'w')
    for index, r in enumerate(R):
        relation = T[index][0]
        out.write(relation)
        out.write('\n')
        for i in range(rank):
            for j in range(rank):
                out.write(str(r[i,j]))
                if (j < rank - 1):
                    out.write('\t')
            out.write('\n')
        out.write('\n')
    out.close()
    return A, R, rank, edge_dict, node_dict


def evaluate_results(A, R, rank, edge_dict, node_dict):
    print 'Computing output on test split'
    try_makedirs(results_dir)
    settings_file = open(results_dir + "settings.txt", 'w')
    settings_file.write("\"split\":\"" + split_name + "\"\n")
    settings_file.close()
    for relation_ in open(split_dir + 'relations_to_run.tsv'):
        relation = relation_.strip()
        try_makedirs(results_dir + relation)
        score_file = open(results_dir + relation + "/scores.tsv", 'w')
        rel_index = edge_dict.getIndex(relation) - 1
        R_r = R[rel_index]
        test_instances = []
        for line in open(split_dir + relation + '/testing.tsv'):
            test_instances.append(line.strip().split('\t'))

        sources = list(set([node_dict.getIndex(x[0])-1 for x in test_instances]))
        sources.sort()
        S = np.zeros((len(sources), rank))
        for (i, source) in enumerate(sources):
            S[i, :] = A[source]

        targets = list(set([node_dict.getIndex(x[1])-1 for x in test_instances]))
        targets.sort()
        T = np.zeros((len(targets), rank))
        for (i, target) in enumerate(targets):
            T[i, :] = A[target]

        prediction_matrix = np.dot(np.dot(S, R_r), T.T)
        source_scores = defaultdict(list)
        for instance in test_instances:
            source_index = sources.index(node_dict.getIndex(instance[0]) - 1)
            target_index = targets.index(node_dict.getIndex(instance[1]) - 1)
            score = prediction_matrix[source_index, target_index]
            if instance[2] == '1':
                correct = True
            else:
                correct = False
            source_scores[source_index].append((score, target_index, correct))


        for source_index in source_scores:
            source = sources[source_index]
            preds = source_scores[source_index]
            preds.sort(reverse=True)
            for (value, target_index, correct) in preds:
                target = targets[target_index]
                score_file.write(node_dict.getString(source + 1))
                score_file.write("\t")
                score_file.write(node_dict.getString(target + 1))
                score_file.write("\t")
                score_file.write(str(value))
                score_file.write("\t")
                if (correct):
                    score_file.write("*\n")
                    break
                score_file.write("\n")
            score_file.write("\n")
        score_file.close()


if __name__ == '__main__':
    graph_dir = '/home/mg1/pra/graphs/synthetic/small/less_easy/'
    split_name = "synthetic/small/less_easy/"
    split_dir = '/home/mg1/pra/splits/' + split_name
    out_dir = '/home/mg1/pra/comparisons/rescal/synthetic/small/less_easy/'
    results_dir = '/home/mg1/pra/results/rescal/less_easy/rescal/'
    A, R, rank, edge_dict, node_dict = run_rescal(graph_dir, out_dir)
    evaluate_results(A, R, rank, edge_dict, node_dict)





